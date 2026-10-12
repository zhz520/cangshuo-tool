-- Metadata only: these four tools execute in the Android client.
-- Preserve existing operator-managed entries, including disabled/deleted records.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES
(
    'bmi', 'BMI 计算', '按中国成人标准与 WHO 标准计算 BMI、体重分类和健康体重范围。',
    (SELECT id FROM tool_category WHERE code = 'CALC'),
    'bmi', JSON_ARRAY('bmi', '体重指数', '体重', '身高', '健康', '超重', '肥胖', 'body mass index', 'weight'),
    'LOCAL', 0, 'ENABLED', 1, 30, 0
),
(
    'compound_interest', '复利计算器', '估算本金与定期追加在复利下的终值、累计投入和收益，支持按月或按年复利。',
    (SELECT id FROM tool_category WHERE code = 'CALC'),
    'compound_interest', JSON_ARRAY('compound', 'interest', '复利', '复利计算', '理财', '收益', '年化', '投资', 'investment'),
    'LOCAL', 0, 'ENABLED', 1, 35, 0
),
(
    'mortgage', '房贷计算', '按等额本息或等额本金估算月供、总利息和逐期还款明细。',
    (SELECT id FROM tool_category WHERE code = 'CALC'),
    'mortgage', JSON_ARRAY('mortgage', 'loan', '房贷', '贷款', '月供', '等额本息', '等额本金', '利率', '还款'),
    'LOCAL', 0, 'ENABLED', 1, 40, 0
),
(
    'fuel', '油耗计算', '根据行驶里程与加油量换算百公里油耗、km/L 和 MPG，可估算油费。',
    (SELECT id FROM tool_category WHERE code = 'CALC'),
    'fuel', JSON_ARRAY('fuel', '油耗', '油费', '加油', '百公里油耗', 'mpg', 'l/100km', '里程'),
    'LOCAL', 0, 'ENABLED', 1, 45, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
