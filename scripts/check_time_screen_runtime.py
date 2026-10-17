# -*- coding: utf-8 -*-
"""V74 time_screen static and isolated backend acceptance (Python 2/3).

Offline: python -B scripts/check_time_screen_runtime.py --static
Deployment host: python -B scripts/check_time_screen_runtime.py <candidate-image>

Static checks cover the V74 metadata migration, complete Android resources,
ToolDefinition registration, icon wiring and the local clock domain entry
points. The candidate path reuses the credential-stripped isolated temporary
MySQL/Redis/container harness. It never modifies production and does not
validate Android rendering, full-screen lifecycle, keep-awake behavior or
deployment.
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
CODE = 'time_screen'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=time_screen&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V74__seed_time_screen_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/timescreen')
RESOURCE_NAME = re.compile(r'^time_screen_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [u'时间屏幕', u'全屏时钟', u'桌面时钟', u'数字时钟', u'防烧屏', u'时间显示',
               'clock', 'screensaver', 'fullscreen clock', 'time display']
KEYWORDS_EN = ['clock', 'screensaver', 'fullscreen clock', 'time display', 'desk clock',
               'digital clock', 'burn-in', u'时间屏幕', u'全屏时钟', u'防烧屏']
CONTRACT = {
    'code': CODE,
    'name': u'时间屏幕',
    'description': u'将设备变成全屏数字时钟，支持 12/24 小时、秒数与日期、主题强调色、防烧屏漂移和屏幕常亮。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 626,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Time Screen',
    'description': 'Turn the device into a full-screen digital clock with 12/24-hour formats, '
                   'seconds, date, accent color, burn-in drift and keep-screen controls.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('24-hour seconds format', '"HH:mm:ss"'),
    ('24-hour minute format', '"HH:mm"'),
    ('12-hour seconds format', '"h:mm:ss"'),
    ('12-hour minute format', '"h:mm"'),
    ('localized full date', 'DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)'),
    ('localized zone display', '.getDisplayName(TextStyle.SHORT, locale)'),
    ('zone-id fallback', 'ifBlank { now.zone.id }'),
    ('minimum drift bound', 'const val MIN_DRIFT_RADIUS = 2'),
    ('maximum drift bound', 'const val MAX_DRIFT_RADIUS = 16'),
    ('drift cycle', 'const val DRIFT_CYCLE_SECONDS = 240'),
    ('drift step', 'const val DRIFT_STEP_SECONDS = 10'),
    ('normalized drift radius', 'driftRadiusDp = settings.driftRadiusDp.coerceIn('),
    ('stepped circular motion', 'val stepped = (epochSecond / DRIFT_STEP_SECONDS) * DRIFT_STEP_SECONDS'),
    ('bounded drift offset', 'cos(radians) * radius'),
    ('next refresh delay', 'fun nextDelayMillis(now: ZonedDateTime, showSeconds: Boolean): Long'),
    ('second-boundary delay', '1_000L - now.nano / 1_000_000L'),
    ('minute-boundary delay', '60_000L - millisIntoMinute'),
    ('refresh delay bounds', 'target.coerceIn(100L, 60_000L)'),
)


def read_optional(path):
    try:
        return read_source(path)
    except (IOError, OSError):
        return None


def check_seed(check):
    source = read_optional(SEED_PATH)
    check(source is not None, 'V74 catalog seed exists')
    if source is None:
        return
    migration_dir = os.path.dirname(SEED_PATH)
    v74_files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V74__'))
    check(v74_files == [os.path.basename(SEED_PATH)], 'V74 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V74 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'],
          'V74 display metadata and ordered Chinese keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V74 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*626\s*,\s*0\s*\)", body)
          is not None, 'V74 mode, login, status, version, sort and featured defaults match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V74 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V74 duplicate-key replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_android_resources(check):
    references = set()
    array_references = set()
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                source = read_optional(os.path.join(current, name)) or ''
                references.update(re.findall(
                    r'R\.string\.(time_screen_[A-Za-z0-9_]+)', source))
                array_references.update(re.findall(
                    r'R\.array\.(time_screen_[A-Za-z0-9_]+)', source))
    check({'time_screen_name', 'time_screen_description'}.issubset(references),
          'Android time screen references metadata resources')
    check(array_references == {'time_screen_keywords'},
          'Android time screen references its keyword array once')

    translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH),
                                       ('values-en', ENGLISH, KEYWORDS_EN)):
        source = read_optional(os.path.join(RES_ROOT, locale, 'strings.xml'))
        check(source is not None, 'Android ' + locale + ' time screen resources exist')
        if source is None:
            continue
        resources = ET.fromstring(source.encode('utf-8'))
        strings = [node for node in resources.findall('string')
                   if RESOURCE_NAME.match(node.get('name', ''))]
        names = [node.get('name') for node in strings]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in strings)
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in strings),
              'Android ' + locale + ' time screen strings are unique and nonempty')
        check(set(translations[locale]) == references,
              'Android ' + locale + ' contains exactly the referenced time screen strings')
        check(translations[locale].get('time_screen_name') == expected['name']
              and translations[locale].get('time_screen_description') == expected['description'],
              'Android ' + locale + ' time screen metadata matches')
        arrays = resources.findall("string-array[@name='time_screen_keywords']")
        actual = [(node.text or '').strip() for node in arrays[0].findall('item')] if arrays else []
        check(len(arrays) == 1 and actual == keywords and len(set(actual)) == len(actual)
              and all(actual), 'Android ' + locale + ' ordered time screen keywords match')
    if 'values' in translations and 'values-en' in translations:
        check(set(translations['values']) == set(translations['values-en']),
              'Android time screen Chinese and English string keys match')
        check(all(placeholders(value) == placeholders(translations['values-en'][name])
                  for name, value in translations['values'].items()),
              'Android time screen bilingual format placeholders match')


def check_android_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'TimeScreenToolDefinition.kt')
    definition = read_optional(definition_path) or ''
    fields = (
        ('code', r'code\s*=\s*"time_screen"'),
        ('name', r'name\s*=\s*resources\.getString\(R\.string\.time_screen_name\)'),
        ('description', r'description\s*=\s*resources\.getString\(R\.string\.time_screen_description\)'),
        ('category', r'category\s*=\s*ToolCategory\.LIFE'),
        ('mode', r'mode\s*=\s*ToolMode\.LOCAL'),
        ('icon', r'icon\s*=\s*"time_screen"'),
        ('keywords', r'keywords\s*=\s*resources\.getStringArray\(R\.array\.time_screen_keywords\)\.toList\(\)'),
        ('sort', r'sortOrder\s*=\s*626\b'),
        ('featured', r'isFeatured\s*=\s*false\b'),
    )
    for field, pattern in fields:
        check(len(re.findall(pattern, definition)) == 1, 'Android time screen ToolDefinition ' + field)
    defaults = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/model/ToolMetadata.kt')) or ''
    for field, default in (('requiresLogin', 'false'), ('status', 'ToolStatus.ENABLED'), ('version', '1')):
        value = re.search(r'\b' + field + r'\s*=\s*([^,\n]+)', definition)
        check((value.group(1).strip() == default if value else re.search(
            r'val\s+' + field + r'\s*:\s*\w+\s*=\s*' + re.escape(default) + r'\s*,', defaults) is not None),
            'Android time screen ' + field + ' agrees with catalog defaults')
    check(re.search(r'override\s+val\s+requiredPermissions\s*:\s*List<String>\s*=\s*emptyList\(\)',
                    definition) is not None,
          'Android time screen declares no extra runtime permissions')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] * len(re.findall(
                    r'\bcode\s*=\s*"time_screen"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)],
          'Android time screen code has exactly one ToolDefinition owner')
    container = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt')) or ''
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and 'ToolRegistry(' in registry.group(0)
          and len(re.findall(r'\bTimeScreenToolDefinition\s*\(', registry.group(0))) == 1,
          'Android bundled ToolRegistry registers time_screen exactly once')
    icons = read_optional(os.path.join(JAVA_ROOT,
                                       'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt')) or ''
    check(len(re.findall(r'"time_screen"\s*->', icons)) == 1 and re.search(
        r'"time_screen"\s*->\s*R\.drawable\.ic_time_screen\b', icons) is not None,
        'Android time screen has one explicit icon mapping')
    icon_path = os.path.join(RES_ROOT, 'drawable/ic_time_screen.xml')
    icon = ET.parse(icon_path).getroot() if os.path.isfile(icon_path) else None
    android = '{http://schemas.android.com/apk/res/android}'
    check(icon is not None and icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
          and icon.get(android + 'height') == '24dp'
          and icon.get(android + 'viewportWidth') == '24' and icon.get(android + 'viewportHeight') == '24'
          and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
          'Android time screen ships a nonempty 24dp vector icon')


def check_domain_features(check):
    domain_path = os.path.join(FEATURE_ROOT, 'domain/TimeScreenModels.kt')
    domain = read_optional(domain_path)
    check(domain is not None, 'Android time screen domain model exists')
    if domain is None:
        return
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'time screen domain provides ' + label)
    view_model = read_optional(os.path.join(FEATURE_ROOT, 'ui/TimeScreenViewModel.kt')) or ''
    check('delay(readTime.nextDelayMillis(settings.showSeconds))' in view_model
          and 'if (!foreground) return' in view_model,
          'time screen refreshes at the calculated boundary only while foregrounded')
    check('TimeScreenPolicy.driftOffset(snapshot.now.toEpochSecond(), settings.driftRadiusDp)'
          in view_model and 'DriftOffset(0f, 0f)' in view_model,
          'time screen applies bounded drift only when anti-burn-in is enabled')


def check_candidate_isolation(check):
    script = read_optional(os.path.join(ROOT, 'scripts/check_time_screen_runtime.py')) or ''
    harness = read_optional(os.path.join(ROOT, 'scripts/check_admin_statistics_runtime.py')) or ''
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'time screen candidate opts into credential-stripped isolation')
    check('codex-stats-redis-' in harness and "'redis-server', '--save', '', '--appendonly', 'no'" in harness,
          'strict candidate uses disposable Redis instead of production credentials')
    check("if not strict_isolation:\n            args.extend(['--network', edge])" in harness
          and "temporary_network = 'codex-stats-net-'" in harness
          and "temporary_network if strict_isolation else network" in harness,
          'strict candidate uses a temporary network instead of production app networks')
    check("MEDIA_ENABLED='false'" in harness and "ZEPP_ENABLED='false'" in harness
          and "STORAGE_ENABLED='false'" in harness,
          'strict candidate disables external integration paths')


def check_static(check):
    check_seed(check)
    check_android_resources(check)
    check_android_registration(check)
    check_domain_features(check)
    check_candidate_isolation(check)


def offline_acceptance():
    checks = [0]
    failures = []

    def check(condition, label):
        checks[0] += 1
        if condition:
            print('PASS', checks[0], label)
        else:
            failures.append(label)
            print('FAIL', checks[0], label)

    check_static(check)
    print('Time screen offline static acceptance:', checks[0] - len(failures), '/', checks[0],
          'checks passed;', len(failures), 'failed; no network, Docker, database or Android runtime')
    if failures:
        raise SystemExit(1)


def verify(check, request, sql, admin):
    """Mutations occur only in the isolated harness database through its callbacks."""
    check_static(check)
    source = read_source(SEED_PATH)
    defaults = dict(CONTRACT, configJson='{}')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if path.startswith('admin/statistics') and status == 200:
            check(cache == 'no-store', 'statistics responses forbid caching')
        return body.get('data')

    def records(path=CATALOG, token=None):
        return check_catalog_page(check, call(path, token=token), path)

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='74' AND success=1") == '1',
          'V74 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V74 creates one time screen row')
    check(CODE in records(), 'public LIFE catalog includes time_screen')
    check_tool_metadata(check, records()[CODE], 'public time screen', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin time screen detail', defaults)
    check_tool_metadata(check, listing, 'admin time screen list', defaults)
    check(detail == listing, 'admin time screen detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers the enabled LIFE category')

    edited = dict(defaults,
                  name=u'时间屏幕验收',
                  description=u'后台编辑的时间屏幕说明。',
                  icon=CODE + '_admin_check',
                  keywords=[u'验收', 'time-screen-admin'],
                  sortOrder=627,
                  version=2,
                  featured=True,
                  requiresLogin=True,
                  configJson='{"acceptance":"time_screen"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited time screen metadata', edited)
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE) == '0'
          and sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE) == '0',
          'fresh time screen starts with zero usage')
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')

    queries = [
        "SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
        "SELECT * FROM flyway_schema_history WHERE version='74'",
        "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
        "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE,
    ]
    snapshot = [sql(query) for query in queries]
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V74 replay preserves operator edits, configuration, timestamps and usage')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V74 replay keeps one time screen row')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin),
                        'seed replay preserves edited time screen', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored time screen metadata', defaults)

    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'time screen increments daily and all-time usage')
    beijing_daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily "
                        "WHERE tool_code='%s' AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE)
    check(beijing_daily == '2', 'time screen daily opens use the Beijing date')
    popular_rows = call('tools/popular?limit=50')
    check(len(popular_rows) <= 50 and len(set(row['code'] for row in popular_rows)) == len(popular_rows)
          and all(row['useCount'] >= 1 for row in popular_rows),
          'popular tools are bounded, unique and used')
    popular = dict((row['code'], row) for row in popular_rows).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'time screen participates in popularity')

    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            groups = [row for row in data[group] if row['code'] == group_code]
            check(len(groups) == 1 and groups[0]['count'] >= 1 and groups[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes time screen usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2
              and ranks[0]['name'] == CONTRACT['name'] and ranks[0]['status'] == 'ENABLED',
              'time screen participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_time_screen_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
