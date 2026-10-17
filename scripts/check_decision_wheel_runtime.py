# -*- coding: utf-8 -*-
"""V75 decision_wheel static and isolated backend acceptance (Python 2/3).

Offline: python -B scripts/check_decision_wheel_runtime.py --static
Deployment host: python -B scripts/check_decision_wheel_runtime.py <candidate-image>
"""
from __future__ import print_function
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'decision_wheel'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=decision_wheel&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V75__seed_decision_wheel_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/decisionwheel')
RESOURCE_NAME = re.compile(r'^decision_wheel_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [
    u'做个决定', u'随机选择', u'转盘', u'抽签', u'抉择', u'选择困难', u'权重',
    'decision', 'wheel', 'spinner', 'random', 'picker',
]
KEYWORDS_EN = [
    'decision wheel', 'random picker', 'spinner', 'wheel', 'weighted choice',
    'what to eat', 'choose', u'决定', u'转盘', u'抽签', u'随机', u'选择',
]
CONTRACT = {
    'code': CODE,
    'name': u'做个决定',
    'description': u'在可自定义的权重转盘中随机选择结果，支持快捷主题、历史记录、复制与分享。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 627,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Decision Wheel',
    'description': 'Pick a random result on a customizable weighted wheel, with quick themes, history and shareable results.',
    'keywords': KEYWORDS_EN,
}
ARRAY_NAMES = {
    'decision_wheel_keywords',
    'decision_wheel_default_options',
    'decision_wheel_preset_meals',
    'decision_wheel_preset_yes_no',
    'decision_wheel_preset_activities',
}
DOMAIN_FEATURES = (
    ('option limit', 'const val MAX_OPTIONS = 12'),
    ('minimum options', 'const val MIN_OPTIONS = 2'),
    ('weight range', 'const val MAX_WEIGHT = 10'),
    ('label limit', 'const val MAX_LABEL_CODE_POINTS = 24'),
    ('history limit', 'const val MAX_HISTORY = 30'),
    ('turn lower bound', 'const val MIN_TURNS = 4'),
    ('turn spread', 'const val EXTRA_TURNS = 2'),
    ('cluster-safe labels', 'UnicodeText.takeCodePointsWithoutSplittingCluster'),
    ('NFKC duplicate check', 'Normalizer.Form.NFKC'),
    ('weighted eligible pool', 'val eligible = options.indices.filter'),
    ('weighted ticket walk', 'if (ticket < weight) return index'),
    ('top-pointer rotation mapping', 'val desiredModulo = floorMod(-pointerPosition, 360f)'),
    ('4-6 turn clamp', 'turns.coerceIn(MIN_TURNS, MIN_TURNS + EXTRA_TURNS)'),
    ('positive modulo helper', 'fun floorMod(value: Float, modulus: Float)'),
    ('bounded option projection', 'fun option(label: String, weight: Int): WheelOption'),
    ('spin plan', 'fun plan('),
    ('result timestamp', 'fun result(spin: WheelSpin, options: List<WheelOption>): WheelResult?'),
)
UI_FEATURES = (
    ('animated wheel', 'rotation.animateTo('),
    ('easing', 'CubicBezierEasing(0.22f, 0f, 0.18f, 1f)'),
    ('drag rotation', 'detectDragGestures('),
    ('pointer rotation updates', 'change.position'),
    ('result transition', 'expandVertically('),
    ('bounded option editor', 'DecisionWheelPolicy.MAX_LABEL_CODE_POINTS'),
    ('history expansion', 'showAllHistory'),
    ('report actions', 'SystemReportActions('),
    ('saved dialog state', 'rememberSaveable'),
    ('restored wheel angle', 'Animatable(state.rotation)'),
    ('drag job cancellation', 'dragJob?.cancelAndJoin()'),
    ('animation cancellation recovery', 'rotation.snapTo(spin.targetRotation)'),
    ('numbered narrow sectors', '(index + 1).toString()'),
)


def read_optional(path):
    try:
        return read_source(path)
    except (IOError, OSError):
        return None


def check_seed(check):
    source = read_optional(SEED_PATH)
    check(source is not None, 'V75 catalog seed exists')
    if source is None:
        return
    migration_dir = os.path.dirname(SEED_PATH)
    v75_files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V75__'))
    check(v75_files == [os.path.basename(SEED_PATH)], 'V75 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V75 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'],
          'V75 display metadata and ordered Chinese keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V75 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*627\s*,\s*0\s*\)", body)
          is not None, 'V75 mode, login, status, version, sort and featured defaults match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V75 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V75 duplicate-key replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_android_resources(check):
    references = set()
    array_references = set()
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                source = read_optional(os.path.join(current, name)) or ''
                references.update(re.findall(r'R\.string\.(decision_wheel_[A-Za-z0-9_]+)', source))
                array_references.update(re.findall(r'R\.array\.(decision_wheel_[A-Za-z0-9_]+)', source))
    check({'decision_wheel_name', 'decision_wheel_description'}.issubset(references),
          'Android decision wheel references metadata resources')
    check(array_references == ARRAY_NAMES, 'Android decision wheel references every preset/default array')

    translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH),
                                       ('values-en', ENGLISH, KEYWORDS_EN)):
        source = read_optional(os.path.join(RES_ROOT, locale, 'strings.xml'))
        check(source is not None, 'Android ' + locale + ' decision wheel resources exist')
        if source is None:
            continue
        resources = ET.fromstring(source.encode('utf-8'))
        strings = [node for node in resources.findall('string')
                   if RESOURCE_NAME.match(node.get('name', ''))]
        names = [node.get('name') for node in strings]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in strings)
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in strings),
              'Android ' + locale + ' decision wheel strings are unique and nonempty')
        check(set(translations[locale]) == references,
              'Android ' + locale + ' contains exactly the referenced decision wheel strings')
        check(translations[locale].get('decision_wheel_name') == expected['name']
              and translations[locale].get('decision_wheel_description') == expected['description'],
              'Android ' + locale + ' decision wheel metadata matches')
        arrays = dict((node.get('name'), [(item.text or '').strip() for item in node.findall('item')])
                      for node in resources.findall('string-array'))
        check(set(ARRAY_NAMES).issubset(arrays), 'Android ' + locale + ' includes all wheel arrays')
        check(arrays.get('decision_wheel_keywords') == keywords,
              'Android ' + locale + ' ordered decision wheel keywords match')
        check(len(arrays.get('decision_wheel_preset_yes_no', [])) == 3
              and len(arrays.get('decision_wheel_default_options', [])) >= 2
              and len(set(arrays.get('decision_wheel_default_options', []))) == len(arrays.get('decision_wheel_default_options', [])),
              'Android ' + locale + ' preset/default option counts are valid')
    if 'values' in translations and 'values-en' in translations:
        check(set(translations['values']) == set(translations['values-en']),
              'Android decision wheel Chinese and English string keys match')
        check(all(placeholders(value) == placeholders(translations['values-en'][name])
                  for name, value in translations['values'].items()),
              'Android decision wheel bilingual format placeholders match')


def check_android_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'DecisionWheelToolDefinition.kt')
    definition = read_optional(definition_path) or ''
    fields = (
        ('code', r'code\s*=\s*"decision_wheel"'),
        ('name', r'name\s*=\s*resources\.getString\(R\.string\.decision_wheel_name\)'),
        ('description', r'description\s*=\s*resources\.getString\(R\.string\.decision_wheel_description\)'),
        ('category', r'category\s*=\s*ToolCategory\.LIFE'),
        ('mode', r'mode\s*=\s*ToolMode\.LOCAL'),
        ('icon', r'icon\s*=\s*"decision_wheel"'),
        ('keywords', r'keywords\s*=\s*resources\.getStringArray\(R\.array\.decision_wheel_keywords\)\.toList\(\)'),
        ('sort', r'sortOrder\s*=\s*627\b'),
        ('featured', r'isFeatured\s*=\s*false\b'),
    )
    for field, pattern in fields:
        check(len(re.findall(pattern, definition)) == 1, 'Android decision wheel ToolDefinition ' + field)
    check(re.search(r'override\s+val\s+requiredPermissions\s*:\s*List<String>\s*=\s*emptyList\(\)',
                    definition) is not None, 'Android decision wheel declares no extra runtime permissions')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] * len(re.findall(
                    r'\bcode\s*=\s*"decision_wheel"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)],
          'Android decision wheel code has exactly one ToolDefinition owner')
    container = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt')) or ''
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and 'ToolRegistry(' in registry.group(0)
          and len(re.findall(r'\bDecisionWheelToolDefinition\s*\(', registry.group(0))) == 1,
          'Android bundled ToolRegistry registers decision_wheel exactly once')
    icons = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt')) or ''
    check(len(re.findall(r'"decision_wheel"\s*->', icons)) == 1 and re.search(
        r'"decision_wheel"\s*->\s*R\.drawable\.ic_decision_wheel\b', icons) is not None,
        'Android decision wheel has one explicit icon mapping')
    icon_path = os.path.join(RES_ROOT, 'drawable/ic_decision_wheel.xml')
    icon = ET.parse(icon_path).getroot() if os.path.isfile(icon_path) else None
    android = '{http://schemas.android.com/apk/res/android}'
    check(icon is not None and icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
          and icon.get(android + 'height') == '24dp'
          and icon.get(android + 'viewportWidth') == '24' and icon.get(android + 'viewportHeight') == '24'
          and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
          'Android decision wheel ships a nonempty 24dp vector icon')


def check_domain_and_ui(check):
    domain = read_optional(os.path.join(FEATURE_ROOT, 'domain/DecisionWheelModels.kt'))
    check(domain is not None, 'Android decision wheel domain model exists')
    if domain is None:
        return
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'decision wheel domain provides ' + label)
    repository = read_optional(os.path.join(FEATURE_ROOT, 'data/LocalDecisionWheelRepository.kt')) or ''
    check('SecureRandom()' in repository and 'random.nextInt(bound)' in repository,
          'decision wheel uses bounded SecureRandom draws')
    view_model = read_optional(os.path.join(FEATURE_ROOT, 'ui/DecisionWheelViewModel.kt')) or ''
    check('if (current.spinning) return' in view_model and 'saved["dwLabels"]' in view_model,
          'decision wheel prevents concurrent spins and persists options')
    check('saved["dwRotation"]' in view_model and 'saved["dwResultVisible"]' in view_model,
          'decision wheel persists settled rotation and result visibility')
    screen = read_optional(os.path.join(FEATURE_ROOT, 'ui/DecisionWheelScreen.kt')) or ''
    canvas = read_optional(os.path.join(FEATURE_ROOT, 'ui/DecisionWheelCanvas.kt')) or ''
    ui = screen + '\n' + canvas
    for label, token in UI_FEATURES:
        check(token in ui, 'decision wheel UI provides ' + label)
    check('onSettled(spin.id, spin.targetRotation)' in screen,
          'decision wheel settles only after the result animation and saves its angle')
    check('if (!enabled) this else' in screen, 'decision wheel disables drags while spinning')


def check_candidate_isolation(check):
    script = read_optional(os.path.join(ROOT, 'scripts/check_decision_wheel_runtime.py')) or ''
    harness = read_optional(os.path.join(ROOT, 'scripts/check_admin_statistics_runtime.py')) or ''
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'decision wheel candidate opts into credential-stripped isolation')
    check('codex-stats-redis-' in harness and "'redis-server', '--save', '', '--appendonly', 'no'" in harness,
          'strict candidate uses disposable Redis instead of production credentials')
    check("if not strict_isolation:\n            args.extend(['--network', edge])" in harness
          and "temporary_network = 'codex-stats-net-'" in harness
          and "temporary_network if strict_isolation else network" in harness,
          'strict candidate uses a temporary network instead of production app networks')


def check_static(check):
    check_seed(check)
    check_android_resources(check)
    check_android_registration(check)
    check_domain_and_ui(check)
    check_candidate_isolation(check)


def offline_acceptance():
    checks = [0]

    def check(condition, label):
        if not condition:
            raise AssertionError(label)
        checks[0] += 1
        print('PASS', checks[0], label)

    check_static(check)
    print('Decision wheel offline static acceptance:', checks[0], '/', checks[0],
          'checks passed; 0 failed; no network, Docker, database or Android runtime')


def verify(check, request, sql, admin):
    check_static(check)
    defaults = dict(CONTRACT, configJson='{}')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if path.startswith('admin/statistics') and status == 200:
            check(cache == 'no-store', 'statistics responses forbid caching')
        return body.get('data')

    def records(path=CATALOG, token=None):
        return check_catalog_page(check, call(path, token=token), path)

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='75' AND success=1") == '1',
          'V75 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V75 creates one decision wheel row')
    check(CODE in records(), 'public LIFE catalog includes decision_wheel')
    check_tool_metadata(check, records()[CODE], 'public decision wheel', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin decision wheel detail', defaults)
    check_tool_metadata(check, listing, 'admin decision wheel list', defaults)
    check(detail == listing, 'admin decision wheel detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers the enabled LIFE category')

    edited = dict(defaults,
                  name=u'权重转盘验收',
                  description=u'后台编辑的权重转盘说明。',
                  icon=CODE + '_admin_check',
                  keywords=[u'验收', 'decision-wheel-admin'],
                  sortOrder=628,
                  version=2,
                  featured=True,
                  requiresLogin=True,
                  configJson='{"acceptance":"decision_wheel"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited decision wheel metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')

    queries = [
        "SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
        "SELECT * FROM flyway_schema_history WHERE version='75'",
        "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
        "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE,
    ]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V75 replay preserves operator edits, configuration, timestamps and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin),
                        'seed replay preserves edited decision wheel', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored decision wheel metadata', defaults)

    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'decision wheel increments daily and all-time usage')
    beijing_daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily "
                        "WHERE tool_code='%s' AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE)
    check(beijing_daily == '2', 'decision wheel daily opens use the Beijing date')
    popular_rows = call('tools/popular?limit=50')
    popular = dict((row['code'], row) for row in popular_rows).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'decision wheel participates in popularity')

    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            groups = [row for row in data[group] if row['code'] == group_code]
            check(len(groups) == 1 and groups[0]['count'] >= 1 and groups[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes decision wheel usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2
              and ranks[0]['name'] == CONTRACT['name'] and ranks[0]['status'] == 'ENABLED',
              'decision wheel participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_decision_wheel_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
