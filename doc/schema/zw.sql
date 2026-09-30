-- zw 城市供水水质检测调度与达标管理 -- schema (hai-007)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：hai_007

CREATE TABLE IF NOT EXISTS t_zw_arch_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '水源地/水厂档案卡号',
  node_no int DEFAULT NULL COMMENT '本卡版本次序',
  site_id int DEFAULT NULL COMMENT '所属底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡供水规模(万吨/日)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新增供水规模(万吨/日)',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版本次序走）',
  grade_level int DEFAULT NULL COMMENT '供水规模折到的保障强度层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水源地/水厂档案卡';

CREATE TABLE IF NOT EXISTS t_zw_case_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '处置工单号',
  stage int DEFAULT NULL COMMENT '当前处置段 0..3（登记/初检/处置/复核封卷）',
  status int DEFAULT NULL COMMENT '工单落定 0未起 1在办 2已封存',
  content varchar(255) DEFAULT NULL COMMENT '工单记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质异常处置工单';

CREATE TABLE IF NOT EXISTS t_zw_imp_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '检测数据上传批次号',
  row_no int DEFAULT NULL COMMENT '原表行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被校验出的数据项代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行检测读数',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已入库 2退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检测数据导入核对行';

CREATE TABLE IF NOT EXISTS t_zw_lim_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '水质限值判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '判定线名',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '关注档指标上限',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '超标档指标上限',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '严重超标档指标上限',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '判定线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '判定线情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质指标限值分档判定线';

CREATE TABLE IF NOT EXISTS t_zw_sam_plan (
  id bigint NOT NULL COMMENT '主键',
  plan_no varchar(64) DEFAULT NULL COMMENT '检测计划周期代号',
  site_no varchar(64) DEFAULT NULL COMMENT '点位代号（派单时带入事项条）',
  team_id bigint DEFAULT NULL COMMENT '直挂承接班组(部门主键)；空=派发时按各班组当前负载加权分派',
  period_days int DEFAULT NULL COMMENT '周期自然日数（隔多少天采一次）',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前浮出待办的自然日数（全站唯一可调档）',
  first_due date DEFAULT NULL COMMENT '首个约定采样日（其后按周期顺排）',
  eff_start datetime DEFAULT NULL COMMENT '计划生效之时',
  eff_end datetime DEFAULT NULL COMMENT '计划停用之时(不含)；空=长期现行',
  content varchar(255) DEFAULT NULL COMMENT '采样事由',
  status int DEFAULT NULL COMMENT '计划情形 0现行 1停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sam_plan_no (plan_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采样检测计划周期底档';

CREATE TABLE IF NOT EXISTS t_zw_sam_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '采样事项号（计划代号-采样日，系统一次生成）',
  plan_no varchar(64) DEFAULT NULL COMMENT '检测计划周期代号（底档带入）',
  site_no varchar(64) DEFAULT NULL COMMENT '点位代号（底档带入）',
  team_id bigint DEFAULT NULL COMMENT '承接班组(部门主键)；空=派发时按当前负载加权分派后回写',
  due_at datetime DEFAULT NULL COMMENT '约定采样那一天的终结时刻(当日23:59:59)——全站唯一口径，任何动作不得挪动',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前浮出待办的自然日数（唯一可调档）',
  dispatch_at datetime DEFAULT NULL COMMENT '真正派出时刻（首次钉库，后到动作不得覆盖）',
  cancel_at datetime DEFAULT NULL COMMENT '撤单时刻',
  content varchar(255) DEFAULT NULL COMMENT '采样事由与受派班组记要',
  hang_reason varchar(255) DEFAULT NULL COMMENT '挂起原因（点位退役/班组停班/无班可派）',
  status int DEFAULT NULL COMMENT '条目情形 0待派 1已派 2挂起 3撤单 4转作他理',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（撤单/转理留痕追加于此）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sam_item_no (item_no),
  UNIQUE KEY uk_sam_plan_site_due (plan_no, site_no, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采样任务到期派单';

CREATE TABLE IF NOT EXISTS t_zw_source (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '水源地/水厂档案代号',
  site_name varchar(128) DEFAULT NULL COMMENT '档案名称',
  site_type varchar(32) DEFAULT NULL COMMENT '档案类别(水源地/水厂)',
  road_name varchar(128) DEFAULT NULL COMMENT '所属行政区划(省—市—区—街道)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单档供水规模一档上限(万吨/日)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限(万吨/日)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限(万吨/日)',
  status int DEFAULT NULL COMMENT '底档情形 0在用 1已退役',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水源地/水厂底档';

CREATE TABLE IF NOT EXISTS t_zw_stop_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '停供/限供申请签核单号',
  node_no int DEFAULT NULL COMMENT '当前所在道 0..2（业务提/水质核/调度准）',
  sign_mode int DEFAULT NULL COMMENT '同口并印办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本口应落印数',
  sign_count int DEFAULT NULL COMMENT '本口已落印数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='停供/限供申请签核单';

-- 初始档案数据：id=0 在用、id=1 已退役
-- 供水规模上限 30/80/200 万吨/日：等于上限算高一档。
INSERT IGNORE INTO t_zw_source (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'ZS00', '青坪市城北水源地（在用）', '水源地', '青坪省—青坪市—城东区—东湖街道', 30.00, 80.00, 200.00, 0, 0, 'seed', NOW()),
       (1, 'ZS01', '青坪市城南水厂（已退役）', '水厂', '青坪省—青坪市—城南区—望湖街道', 30.00, 80.00, 200.00, 1, 0, 'seed', NOW());

-- 检测计划周期底档种子：
-- PL-ZS00-M：城北水源地月度例行（30 天一周期，提前 3 天浮出，班组不直挂→按负载加权派班）；
-- PL-ZS01-Q：城南水厂季度（直挂 2 号班组；点位已退役，开单前即转挂起，用来演示挂起不拖垮整轮）。
INSERT IGNORE INTO t_zw_sam_plan (id, plan_no, site_no, team_id, period_days, amount, first_due, eff_start, eff_end, content, status, del_flag, create_by, create_time)
VALUES (10, 'PL-ZS00-M', 'ZS00', NULL, 30, 3.00, '2026-09-01', '2026-09-01 00:00:00', NULL, '城北水源地月度例行采样', 0, 0, 'seed', NOW()),
       (11, 'PL-ZS01-Q', 'ZS01', 2, 90, 3.00, '2026-09-15', '2026-09-15 00:00:00', NULL, '城南水厂季度采样（点位已退役，开单即挂起）', 0, 0, 'seed', NOW());

