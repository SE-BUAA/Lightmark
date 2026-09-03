INSERT INTO `user` (id, phone, email, password, nickname, avatar, points, level, status, register_source, create_time, update_time, deleted, gender)
VALUES
  (1, '13800000000', 'admin@lightmark.com', '$2a$10$8.1/eMI.V.pjn4rjOsgfdOnE6sJ6oLumLHAYVtJpiXPEzOiZ993Nm', '系统管理员', '', 0, 3, 0, 'PHONE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, 0),
  (2, '13900000000', 'user@lightmark.com', '$2a$10$8.1/eMI.V.pjn4rjOsgfdOnE6sJ6oLumLHAYVtJpiXPEzOiZ993Nm', '普通用户', '', 120, 1, 0, 'PHONE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO user_role (user_id, role_id) VALUES
  (1, 1),
  (2, 2);

INSERT INTO admin_log (admin_id, operation, params, result, ip, create_time) VALUES
  (1, 'seed_admin_log', 'init', 'SUCCESS', '127.0.0.1', CURRENT_TIMESTAMP);
