package top.ortus.lightmark.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.user.dao.PointsLogRepository;
import top.ortus.lightmark.user.dao.UserRepositoryImpl;
import top.ortus.lightmark.user.dto.module.PointsLogDTO;

import java.util.List;

@Service
public class PointsService {
    private final UserRepositoryImpl userRepository;
    private final PointsLogRepository pointsLogRepository;

    public PointsService(UserRepositoryImpl userRepository, PointsLogRepository pointsLogRepository) {
        this.userRepository = userRepository;
        this.pointsLogRepository = pointsLogRepository;
    }

    public List<PointsLogDTO> logs(String userId) {
        requireUser(userId);
        return pointsLogRepository.findByUserId(userId);
    }

    @Transactional
    public boolean change(String userId, int amount, int type, String source, String orderId) {
        requireUser(userId);
        if (amount == 0 || source == null || source.isBlank()) throw new ApiException(400, "invalid points request");
        if (orderId != null && !orderId.isBlank() && userRepository.countPointsLog(userId, orderId, source) > 0) return true;
        int changed = userRepository.changePoints(userId, amount);
        if (changed == 0) throw new ApiException(404, "user not found");
        userRepository.insertPointsLog(userId, type, amount, source, orderId);
        return true;
    }

    private void requireUser(String userId) {
        if (userId == null || userId.isBlank() || "0".equals(userId)) throw new ApiException(401, "unauthorized");
    }
}
