import argparse
import csv
import json
import math
import os
import statistics
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime

import psutil
import requests


ENDPOINTS = {
    "admin_login": {
        "method": "POST",
        "path": "/api/auth/admin/login",
        "json": {"account": "admin@lightmark.com", "password": "password"},
    },
    "admin_users": {
        "method": "GET",
        "path": "/api/admin/users",
        "requires_token": True,
    },
    "admin_logs": {
        "method": "GET",
        "path": "/api/admin/logs",
        "requires_token": True,
    },
}


def ensure_dir(path):
    os.makedirs(path, exist_ok=True)


class Sampler:
    def __init__(self, pid, sample_file, interval=1.0):
        self.process = psutil.Process(pid)
        self.sample_file = sample_file
        self.interval = interval
        self.samples = []
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, daemon=True)

    def start(self):
        self.process.cpu_percent(interval=None)
        self._thread.start()

    def stop(self):
        self._stop.set()
        self._thread.join(timeout=5)

    def _run(self):
        with open(self.sample_file, "w", encoding="utf-8", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["timestamp", "cpu_percent", "rss_mb"])
            while not self._stop.is_set():
                ts = datetime.now().isoformat(timespec="seconds")
                try:
                    cpu = self.process.cpu_percent(interval=None)
                    rss = self.process.memory_info().rss / 1024 / 1024
                except psutil.Error:
                    break
                writer.writerow([ts, f"{cpu:.2f}", f"{rss:.2f}"])
                f.flush()
                self.samples.append((cpu, rss))
                time.sleep(self.interval)

    def summary(self):
        cpus = [x[0] for x in self.samples] or [0.0]
        rsses = [x[1] for x in self.samples] or [0.0]
        return {
            "cpu_avg": round(statistics.mean(cpus), 2),
            "cpu_peak": round(max(cpus), 2),
            "rss_avg_mb": round(statistics.mean(rsses), 2),
            "rss_peak_mb": round(max(rsses), 2),
        }


def admin_token(base_url):
    resp = requests.post(
        base_url.rstrip("/") + ENDPOINTS["admin_login"]["path"],
        json=ENDPOINTS["admin_login"]["json"],
        timeout=15,
    )
    resp.raise_for_status()
    data = resp.json().get("data", {})
    token = data.get("token")
    if not token:
        raise RuntimeError(f"admin login missing token: {resp.text}")
    return token


def one_request(base_url, endpoint_name, token):
    endpoint = ENDPOINTS[endpoint_name]
    method = endpoint["method"]
    url = base_url.rstrip("/") + endpoint["path"]
    headers = {}
    if endpoint.get("requires_token"):
        headers["Authorization"] = f"Bearer {token}"
    start = time.perf_counter()
    ok = False
    status = None
    error = ""
    try:
        response = requests.request(
            method,
            url,
            json=endpoint.get("json"),
            headers=headers,
            timeout=30,
        )
        status = response.status_code
        if response.status_code == 200:
            body = response.json()
            ok = body.get("code") == 0
            if not ok:
                error = json.dumps(body, ensure_ascii=False)
        else:
            error = response.text[:300]
    except Exception as ex:
        error = str(ex)
    latency_ms = (time.perf_counter() - start) * 1000
    return {
        "ok": ok,
        "status": status if status is not None else 0,
        "latency_ms": latency_ms,
        "error": error,
    }


def percentile(values, p):
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = max(0, math.ceil(p * len(ordered)) - 1)
    return ordered[idx]


def run_case(base_url, endpoint_name, version, run_no, total_requests, concurrency, pid, out_dir):
    case_prefix = f"{version}-{endpoint_name}-run{run_no}"
    sample_file = os.path.join(out_dir, f"{case_prefix}-metrics.csv")
    raw_file = os.path.join(out_dir, f"{case_prefix}-raw.csv")
    sampler = Sampler(pid, sample_file, interval=1.0)
    token = None
    if ENDPOINTS[endpoint_name].get("requires_token"):
        token = admin_token(base_url)
    start = time.perf_counter()
    sampler.start()
    results = []
    try:
        with ThreadPoolExecutor(max_workers=concurrency) as executor:
            futures = [executor.submit(one_request, base_url, endpoint_name, token) for _ in range(total_requests)]
            for future in as_completed(futures):
                results.append(future.result())
    finally:
        sampler.stop()
    elapsed = time.perf_counter() - start

    with open(raw_file, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["ok", "status", "latency_ms", "error"])
        for item in results:
            writer.writerow([item["ok"], item["status"], f"{item['latency_ms']:.2f}", item["error"]])

    latencies = [item["latency_ms"] for item in results]
    failed = sum(1 for item in results if not item["ok"])
    metrics = sampler.summary()
    return {
        "endpoint": endpoint_name,
        "version": version,
        "run": run_no,
        "concurrency": concurrency,
        "requests": total_requests,
        "throughput_rps": round((len(results) / elapsed) if elapsed > 0 else 0.0, 2),
        "avg_ms": round(statistics.mean(latencies) if latencies else 0.0, 2),
        "p95_ms": round(percentile(latencies, 0.95), 2),
        "failed": failed,
        "error_rate_pct": round((failed / total_requests) * 100, 2),
        "cpu_avg": metrics["cpu_avg"],
        "cpu_peak": metrics["cpu_peak"],
        "rss_avg_mb": metrics["rss_avg_mb"],
        "rss_peak_mb": metrics["rss_peak_mb"],
        "raw_file": raw_file,
        "metrics_file": sample_file,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mono-base", required=True)
    parser.add_argument("--msa-base", required=True)
    parser.add_argument("--mono-pid", type=int, required=True)
    parser.add_argument("--msa-pid", type=int, required=True)
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--requests", type=int, default=600)
    parser.add_argument("--endpoints", nargs="+", default=["admin_login", "admin_users", "admin_logs"])
    parser.add_argument("--out-dir", required=True)
    args = parser.parse_args()

    ensure_dir(args.out_dir)
    summary_path = os.path.join(args.out_dir, "summary.csv")
    with open(summary_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([
            "endpoint", "version", "run", "concurrency", "requests",
            "throughput_rps", "avg_ms", "p95_ms", "failed", "error_rate_pct",
            "cpu_avg", "cpu_peak", "rss_avg_mb", "rss_peak_mb",
            "raw_file", "metrics_file",
        ])
        for endpoint_name in args.endpoints:
            for run_no in range(1, args.runs + 1):
                for version, base, pid in [
                    ("mono", args.mono_base, args.mono_pid),
                    ("msa", args.msa_base, args.msa_pid),
                ]:
                    row = run_case(
                        base, endpoint_name, version, run_no,
                        args.requests, args.concurrency, pid, args.out_dir,
                    )
                    writer.writerow([
                        row["endpoint"], row["version"], row["run"], row["concurrency"], row["requests"],
                        row["throughput_rps"], row["avg_ms"], row["p95_ms"], row["failed"], row["error_rate_pct"],
                        row["cpu_avg"], row["cpu_peak"], row["rss_avg_mb"], row["rss_peak_mb"],
                        row["raw_file"], row["metrics_file"],
                    ])
                    f.flush()
                    print(json.dumps(row, ensure_ascii=False))


if __name__ == "__main__":
    main()
