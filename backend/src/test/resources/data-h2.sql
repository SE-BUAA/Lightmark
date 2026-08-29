INSERT INTO `user` (id, phone, email, password, nickname, country_code, register_source, points, level, status, deleted) VALUES
(1, '13800000000', 'admin@lightmark.com', '$2a$10$8.1/eMI.V.pjn4rjOsgfdOnE6sJ6oLumLHAYVtJpiXPEzOiZ993Nm', '系统管理员', '+86', 'PHONE', 0, 3, 0, 0),
(2, '13900000000', 'user@lightmark.com', '$2a$10$8.1/eMI.V.pjn4rjOsgfdOnE6sJ6oLumLHAYVtJpiXPEzOiZ993Nm', '普通用户', '+86', 'PHONE', 120, 1, 0, 0);

INSERT INTO role (id, role_name, permission) VALUES
(1, 'ADMIN', '*'),
(2, 'USER', '');

INSERT INTO user_role (user_id, role_id) VALUES
(1, 1),
(2, 2);

INSERT INTO product (id, product_type, name, price, stock, sold_count, status, extra, category_tags) VALUES
(1, 'FLIGHT', 'CA1234 北京-上海', 680.00, 50, 12, 1, '{"airline":"中国国际航空","flightNo":"CA1234","departureCity":"BJS","arrivalCity":"SHA","departureDate":"2026-06-20","departureTime":"08:30","arrivalTime":"10:45","stops":0,"cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取10%手续费"}', NULL),
(2, 'HOTEL', '上海外滩酒店', 850.00, 20, 8, 1, '{"city":"上海","address":"上海市黄浦区中山东一路88号","starLevel":5,"coverImage":"https://example.com/hotel-2.jpg","lat":31.2400,"lng":121.4900,"facilities":["早餐","健身房","停车场"],"brand":"Lightmark Collection"}', NULL),
(3, 'TRAIN', 'G1次', 553.00, 200, 88, 1, '{"start_station":"北京南","end_station":"上海虹桥","date":"2026-12-31","depart_time":"08:00","arrive_time":"12:38"}', '["商务座","一等座","二等座"]'),
(4, 'FLIGHT', 'MU5678 北京-上海', 520.00, 30, 20, 1, '{"airline":"东方航空","flightNo":"MU5678","departureCity":"BJS","arrivalCity":"SHA","departureDate":"2026-06-20","departureTime":"13:15","arrivalTime":"15:25","stops":0,"cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取15%手续费"}', NULL),
(5, 'FLIGHT', 'CZ9001 北京-广州', 760.00, 18, 4, 1, '{"airline":"南方航空","flightNo":"CZ9001","departureCity":"BJS","arrivalCity":"CAN","departureDate":"2026-06-20","departureTime":"09:40","arrivalTime":"12:55","stops":0,"cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"特价舱不可改签"}', NULL),
(6, 'FLIGHT', 'HO2468 北京-上海', 430.00, 0, 2, 1, '{"airline":"吉祥航空","flightNo":"HO2468","departureCity":"BJS","arrivalCity":"SHA","departureDate":"2026-06-20","departureTime":"22:10","arrivalTime":"00:20","stops":0,"cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"15kg","refundRule":"不可退改"}', NULL);

INSERT INTO product (id, product_type, name, price, stock, sold_count, status, extra, category_tags) VALUES
(201, 'VACATION', '三亚海岛五日自由行', 2999.00, 30, 0, 1, '{"destination":"三亚","depart_city":"北京","date":"2026-06-15","days":5,"hotel_level":"五星","summary":"海景酒店连住，含接送机"}', '["海岛","自由行","亲子"]'),
(202, 'VACATION', '云南古城六日跟团游', 2580.00, 24, 0, 1, '{"destination":"丽江","depart_city":"上海","date":"2026-06-20","days":6,"hotel_level":"四星","summary":"丽江大理双城，含经典景点门票"}', '["古城","跟团游","摄影"]'),
(203, 'VACATION', '成都美食四日私享团', 1880.00, 18, 0, 1, '{"destination":"成都","depart_city":"广州","date":"2026-07-02","days":4,"hotel_level":"四星","summary":"小团出行，城市美食和周边慢游"}', '["美食","私享团","城市"]');

INSERT INTO orders (id, order_no, user_id, order_type, pay_amount, payment_method, status) VALUES
(1, 'ORD202604180001', 2, 'FLIGHT', 680.00, 'WECHAT', 1),
(2, 'ORD202604180002', 2, 'HOTEL', 750.00, 'ALIPAY', 1),
(3, 'ORD202604180003', 2, 'TRAIN', 553.00, 'WECHAT', 0);

INSERT INTO room_type (id, hotel_id, room_name, breakfast, cancel_policy, price) VALUES
(21, 2, '标准大床房', 1, 'FREE_CANCEL', 899.00),
(22, 2, '高级双床房', 2, 'LIMITED_CANCEL', 999.00);

INSERT INTO product (id, product_type, name, price, stock, sold_count, status, extra) VALUES
(1001, 'FLIGHT', 'DBG1001 北京大兴-上海浦东', 560.00, 42, 8, 1, '{"airline":"中国国际航空","flightNo":"DBG1001","departureCity":"BJS","departureAirport":"PKX","arrivalCity":"SHA","arrivalAirport":"PVG","departureDate":"2026-07-01","departureTime":"07:25","arrivalTime":"09:40","stops":0,"aircraft":"A321","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取10%手续费"}'),
(1002, 'FLIGHT', 'DBG1002 北京首都-上海虹桥', 630.00, 18, 14, 1, '{"airline":"东方航空","flightNo":"DBG1002","departureCity":"BJS","departureAirport":"PEK","arrivalCity":"SHA","arrivalAirport":"SHA","departureDate":"2026-07-01","departureTime":"12:30","arrivalTime":"14:45","stops":0,"aircraft":"B737-800","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"FIRST","name":"头等舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取15%手续费"}'),
(1003, 'FLIGHT', 'DBG1003 北京大兴-广州白云', 720.00, 26, 11, 1, '{"airline":"南方航空","flightNo":"DBG1003","departureCity":"BJS","departureAirport":"PKX","arrivalCity":"CAN","arrivalAirport":"CAN","departureDate":"2026-07-01","departureTime":"09:10","arrivalTime":"12:25","stops":0,"aircraft":"A320neo","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"特价舱按航司规则退改"}'),
(1004, 'FLIGHT', 'DBG1004 上海浦东-成都天府', 690.00, 33, 17, 1, '{"airline":"四川航空","flightNo":"DBG1004","departureCity":"SHA","departureAirport":"PVG","arrivalCity":"CTU","arrivalAirport":"TFU","departureDate":"2026-07-02","departureTime":"10:20","arrivalTime":"13:35","stops":0,"aircraft":"A330","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"起飞前4小时内收取30%手续费"}'),
(1005, 'FLIGHT', 'DBG1005 成都双流-深圳宝安', 510.00, 21, 9, 1, '{"airline":"深圳航空","flightNo":"DBG1005","departureCity":"CTU","departureAirport":"CTU","arrivalCity":"SZX","arrivalAirport":"SZX","departureDate":"2026-07-02","departureTime":"16:05","arrivalTime":"18:25","stops":0,"aircraft":"B737-900","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取12%手续费"}'),
(1006, 'FLIGHT', 'DBG1006 广州白云-杭州萧山', 390.00, 56, 23, 1, '{"airline":"海南航空","flightNo":"DBG1006","departureCity":"CAN","departureAirport":"CAN","arrivalCity":"HGH","arrivalAirport":"HGH","departureDate":"2026-07-03","departureTime":"08:45","arrivalTime":"10:55","stops":0,"aircraft":"B787","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"按航司实时规则退改"}'),
(1007, 'FLIGHT', 'DBG1007 深圳宝安-西安咸阳', 460.00, 12, 6, 1, '{"airline":"春秋航空","flightNo":"DBG1007","departureCity":"SZX","departureAirport":"SZX","arrivalCity":"XIY","arrivalAirport":"XIY","departureDate":"2026-07-03","departureTime":"21:15","arrivalTime":"23:55","stops":0,"aircraft":"A320","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"15kg","refundRule":"低价舱位退改费用较高"}'),
(1008, 'FLIGHT', 'DBG1008 杭州萧山-北京首都', 580.00, 0, 19, 1, '{"airline":"中国国际航空","flightNo":"DBG1008","departureCity":"HGH","departureAirport":"HGH","arrivalCity":"BJS","arrivalAirport":"PEK","departureDate":"2026-07-04","departureTime":"18:40","arrivalTime":"20:55","stops":0,"aircraft":"A321","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"已售罄样例，用于验证库存过滤"}'),
(1009, 'FLIGHT', 'DBG1009 上海虹桥-北京大兴', 540.00, 24, 31, 0, '{"airline":"吉祥航空","flightNo":"DBG1009","departureCity":"SHA","departureAirport":"SHA","arrivalCity":"BJS","arrivalAirport":"PKX","departureDate":"2026-07-04","departureTime":"06:50","arrivalTime":"09:05","stops":0,"aircraft":"A320","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"下架样例，用于验证状态过滤"}'),
(1010, 'FLIGHT', 'DBG1010 北京大兴-上海浦东 经停济南', 420.00, 15, 4, 1, '{"airline":"山东航空","flightNo":"DBG1010","departureCity":"BJS","departureAirport":"PKX","arrivalCity":"SHA","arrivalAirport":"PVG","departureDate":"2026-07-05","departureTime":"15:20","arrivalTime":"18:45","stops":1,"aircraft":"B737-800","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"经停样例，用于验证经停排序"}');

INSERT INTO product (id, product_type, name, price, stock, sold_count, status, extra) VALUES
(1101, 'FLIGHT', 'DBG1101 北京首都-上海虹桥 暑期早班', 610.00, 35, 12, 1, '{"airline":"中国国际航空","flightNo":"DBG1101","departureCity":"BJS","departureAirport":"PEK","arrivalCity":"SHA","arrivalAirport":"SHA","departureDate":"2026-08-15","departureTime":"08:05","arrivalTime":"10:20","stops":0,"aircraft":"A321","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取10%手续费"}'),
(1102, 'FLIGHT', 'DBG1102 北京大兴-上海浦东 中秋特价', 480.00, 28, 15, 1, '{"airline":"东方航空","flightNo":"DBG1102","departureCity":"BJS","departureAirport":"PKX","arrivalCity":"SHA","arrivalAirport":"PVG","departureDate":"2026-09-18","departureTime":"19:35","arrivalTime":"21:50","stops":0,"aircraft":"B737-800","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取15%手续费"}'),
(1103, 'FLIGHT', 'DBG1103 北京首都-上海虹桥 国庆高峰', 830.00, 40, 36, 1, '{"airline":"中国国际航空","flightNo":"DBG1103","departureCity":"BJS","departureAirport":"PEK","arrivalCity":"SHA","arrivalAirport":"SHA","departureDate":"2026-10-01","departureTime":"07:50","arrivalTime":"10:05","stops":0,"aircraft":"A330","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"FIRST","name":"头等舱"}],"baggage":"20kg","refundRule":"节假日高峰舱位按航司规则退改"}'),
(1104, 'FLIGHT', 'DBG1104 北京大兴-上海浦东 双十一低价', 450.00, 44, 18, 1, '{"airline":"吉祥航空","flightNo":"DBG1104","departureCity":"BJS","departureAirport":"PKX","arrivalCity":"SHA","arrivalAirport":"PVG","departureDate":"2026-11-11","departureTime":"21:10","arrivalTime":"23:25","stops":0,"aircraft":"A320","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"低价舱位退改费用较高"}'),
(1105, 'FLIGHT', 'DBG1105 北京首都-上海浦东 圣诞晚班', 780.00, 22, 20, 1, '{"airline":"东方航空","flightNo":"DBG1105","departureCity":"BJS","departureAirport":"PEK","arrivalCity":"SHA","arrivalAirport":"PVG","departureDate":"2026-12-24","departureTime":"20:15","arrivalTime":"22:30","stops":0,"aircraft":"B787","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取15%手续费"}'),
(1106, 'FLIGHT', 'DBG1106 北京大兴-上海虹桥 春运前', 520.00, 30, 16, 1, '{"airline":"中国联合航空","flightNo":"DBG1106","departureCity":"BJS","departureAirport":"PKX","arrivalCity":"SHA","arrivalAirport":"SHA","departureDate":"2027-01-18","departureTime":"10:45","arrivalTime":"13:00","stops":0,"aircraft":"B737-800","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取12%手续费"}'),
(1107, 'FLIGHT', 'DBG1107 北京首都-上海浦东 情人节', 690.00, 26, 21, 1, '{"airline":"海南航空","flightNo":"DBG1107","departureCity":"BJS","departureAirport":"PEK","arrivalCity":"SHA","arrivalAirport":"PVG","departureDate":"2027-02-14","departureTime":"13:20","arrivalTime":"15:40","stops":0,"aircraft":"B787","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"按航司实时规则退改"}'),
(1108, 'FLIGHT', 'DBG1108 北京大兴-上海浦东 春季低价', 500.00, 48, 10, 1, '{"airline":"山东航空","flightNo":"DBG1108","departureCity":"BJS","departureAirport":"PKX","arrivalCity":"SHA","arrivalAirport":"PVG","departureDate":"2027-03-20","departureTime":"15:10","arrivalTime":"17:25","stops":0,"aircraft":"B737-800","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取10%手续费"}'),
(1109, 'FLIGHT', 'DBG1109 北京首都-上海虹桥 清明出行', 560.00, 32, 14, 1, '{"airline":"中国国际航空","flightNo":"DBG1109","departureCity":"BJS","departureAirport":"PEK","arrivalCity":"SHA","arrivalAirport":"SHA","departureDate":"2027-04-05","departureTime":"09:30","arrivalTime":"11:45","stops":0,"aircraft":"A321","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取10%手续费"}'),
(1110, 'FLIGHT', 'DBG1110 北京大兴-上海浦东 五一高峰', 740.00, 38, 33, 1, '{"airline":"东方航空","flightNo":"DBG1110","departureCity":"BJS","departureAirport":"PKX","arrivalCity":"SHA","arrivalAirport":"PVG","departureDate":"2027-05-01","departureTime":"11:55","arrivalTime":"14:10","stops":0,"aircraft":"A330","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"FIRST","name":"头等舱"}],"baggage":"20kg","refundRule":"节假日高峰舱位按航司规则退改"}'),
(1111, 'FLIGHT', 'DBG1111 上海浦东-成都天府 暑期', 620.00, 36, 19, 1, '{"airline":"四川航空","flightNo":"DBG1111","departureCity":"SHA","departureAirport":"PVG","arrivalCity":"CTU","arrivalAirport":"TFU","departureDate":"2026-08-20","departureTime":"12:10","arrivalTime":"15:20","stops":0,"aircraft":"A330","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"起飞前4小时内收取30%手续费"}'),
(1112, 'FLIGHT', 'DBG1112 成都双流-深圳宝安 秋季', 470.00, 29, 11, 1, '{"airline":"深圳航空","flightNo":"DBG1112","departureCity":"CTU","departureAirport":"CTU","arrivalCity":"SZX","arrivalAirport":"SZX","departureDate":"2026-09-12","departureTime":"17:45","arrivalTime":"20:05","stops":0,"aircraft":"B737-900","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取12%手续费"}'),
(1113, 'FLIGHT', 'DBG1113 广州白云-杭州萧山 秋游', 430.00, 50, 22, 1, '{"airline":"海南航空","flightNo":"DBG1113","departureCity":"CAN","departureAirport":"CAN","arrivalCity":"HGH","arrivalAirport":"HGH","departureDate":"2026-10-18","departureTime":"08:30","arrivalTime":"10:40","stops":0,"aircraft":"B787","cabins":[{"type":"ECONOMY","name":"经济舱"},{"type":"BUSINESS","name":"商务舱"}],"baggage":"20kg","refundRule":"按航司实时规则退改"}'),
(1114, 'FLIGHT', 'DBG1114 深圳宝安-西安咸阳 初冬', 520.00, 18, 8, 1, '{"airline":"春秋航空","flightNo":"DBG1114","departureCity":"SZX","departureAirport":"SZX","arrivalCity":"XIY","arrivalAirport":"XIY","departureDate":"2026-11-06","departureTime":"21:30","arrivalTime":"00:10","stops":0,"aircraft":"A320","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"15kg","refundRule":"低价舱位退改费用较高"}'),
(1115, 'FLIGHT', 'DBG1115 上海虹桥-北京大兴 跨年返程', 680.00, 34, 25, 1, '{"airline":"吉祥航空","flightNo":"DBG1115","departureCity":"SHA","departureAirport":"SHA","arrivalCity":"BJS","arrivalAirport":"PKX","departureDate":"2026-12-31","departureTime":"18:30","arrivalTime":"20:45","stops":0,"aircraft":"A320","cabins":[{"type":"ECONOMY","name":"经济舱"}],"baggage":"20kg","refundRule":"起飞前24小时外收取15%手续费"}');

INSERT INTO travel_plan (id, user_id, title, destination, start_date, end_date, plan_data, is_public) VALUES
(1, 2, '杭州三日慢游', '杭州', '2026-07-01', '2026-07-03', '[{"day":1,"theme":"西湖初见","items":["抵达杭州","漫步白堤","湖滨晚餐"]}]', 1);

INSERT INTO post (id, user_id, title, content, images, likes, comments_count, status) VALUES
(1, 2, '杭州三日慢游路线分享', '西湖适合留出完整半天，龙井村建议上午去。', '["https://picsum.photos/id/104/640/420"]', 1, 1, 1);

INSERT INTO post_like (post_id, user_id) VALUES
(1, 1);

INSERT INTO comment (id, target_type, target_id, user_id, content) VALUES
(1, 'POST', 1, 1, '这条路线很实用，已收藏。');

INSERT INTO question (id, user_id, title, content, answer, answer_user_id, status, answer_time) VALUES
(1, 2, '第一次去成都住哪里方便？', '想吃小吃、坐地铁方便，预算中等。', '可以优先看春熙路、宽窄巷子或太古里周边。', 1, 1, CURRENT_TIMESTAMP);

INSERT INTO review (id, order_id, product_id, target_type, user_id, rating, content, images, status, create_time) VALUES
(1, 2, 2, 'HOTEL', 2, 5, '上海外滩酒店位置很好，去外滩和地铁站都很方便，房间也比较干净。', '[]', 1, CURRENT_TIMESTAMP);
