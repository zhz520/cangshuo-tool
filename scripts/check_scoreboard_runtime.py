# -*- coding: utf-8 -*-
"""V72 scoreboard static and isolated backend acceptance (Python 2/3).

Offline: python -B scripts/check_scoreboard_runtime.py --static
Deployment host: python -B scripts/check_scoreboard_runtime.py <candidate-image>

Static checks cover the V72 metadata migration, complete Android resources,
ToolDefinition registration and the local scoreboard domain entry points. The
candidate path reuses the isolated temporary MySQL/user/container harness. It
never modifies production and does not validate Android gestures, rendering,
timer accuracy or deployment.
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
CODE = 'scoreboard'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=' + CODE + '&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V72__seed_scoreboard_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/scoreboard')
KEYWORDS_ZH = [u'记分牌', u'计分', u'比分', u'两队', u'计时', u'比赛', u'裁判',
               'scoreboard', 'score', 'timer', 'match']
KEYWORDS_EN = ['scoreboard', 'score', 'scorekeeper', 'two teams', 'timer', 'match', 'referee',
               u'记分牌', u'比分', u'计时', u'比赛']
CONTRACT = {
    'code': CODE,
    'name': u'记分牌',
    'description': u'为两队记录比分、定时比赛、切换节次，支持撤销、换边、历史记录与分享。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 624,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Scoreboard',
    'description': 'Track two teams, time a match, advance periods, undo changes, swap sides, '
                   'review history and share results.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('two-side score model', 'data class ScoreboardCore('),
    ('left and right score lookup', 'fun score(side: ScoreSide): Int'),
    ('score add and subtract', 'fun addPoints(session: ScoreboardSession, side: ScoreSide, delta: Int'),
    ('direct score correction', 'fun setScore(session: ScoreboardSession, side: ScoreSide, score: Int'),
    ('side swap', 'fun swap(session: ScoreboardSession, nowEpochMs: Long)'),
    ('next period', 'fun nextPeriod(session: ScoreboardSession, nowEpochMs: Long)'),
    ('team name editing', 'fun editNames('),
    ('undo', 'fun undo(session: ScoreboardSession)'),
    ('reset', 'fun reset(session: ScoreboardSession)'),
    ('timer start', 'fun startTimer('),
    ('timer pause', 'fun pauseTimer('),
    ('timer reset', 'fun resetTimer('),
    ('timer restore', 'fun restoreTimer('),
    ('timer formatting', 'fun formatDuration('),
    ('history encoding', 'fun encodeJournal('),
    ('history decoding', 'fun decodeJournal('),
    ('bounded history', 'const val MAX_HISTORY = 50'),
)


def read_optional(path):
    try:
        return read_source(path)
    except (IOError, OSError):
        return None


def check_seed(check):
    source = read_optional(SEED_PATH)
    check(source is not None, 'V72 catalog seed exists')
    if source is None:
        return
    migration_dir = os.path.dirname(SEED_PATH)
    v72_files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V72__'))
    check(v72_files == [os.path.basename(SEED_PATH)], 'V72 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V72 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'],
          'V72 display metadata and ordered Chinese keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V72 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*624\s*,\s*0\s*\)", body)
          is not None, 'V72 mode, login, status, version, sort and featured defaults match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V72 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V72 duplicate-key replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_android_resources(check):
    references = set()
    array_references = set()
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                source = read_optional(os.path.join(current, name)) or ''
                references.update(re.findall(r'R\.string\.(scoreboard_[A-Za-z0-9_]+)', source))
                array_references.update(re.findall(r'R\.array\.(scoreboard_[A-Za-z0-9_]+)', source))
    check({'scoreboard_name', 'scoreboard_description'}.issubset(references),
          'Android scoreboard references metadata resources')
    check(array_references == {'scoreboard_keywords'},
          'Android scoreboard references its keyword array once')

    translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        source = read_optional(os.path.join(RES_ROOT, locale, 'strings.xml'))
        check(source is not None, 'Android ' + locale + ' scoreboard resources exist')
        if source is None:
            continue
        resources = ET.fromstring(source.encode('utf-8'))
        strings = [node for node in resources.findall('string')
                   if node.get('name', '').startswith('scoreboard_')]
        names = [node.get('name') for node in strings]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in strings)
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in strings),
              'Android ' + locale + ' scoreboard strings are unique and nonempty')
        check(set(translations[locale]) == references,
              'Android ' + locale + ' covers every referenced scoreboard string')
        check(translations[locale].get('scoreboard_name') == expected['name']
              and translations[locale].get('scoreboard_description') == expected['description'],
              'Android ' + locale + ' scoreboard metadata matches')
        arrays = resources.findall("string-array[@name='scoreboard_keywords']")
        actual = [(node.text or '').strip() for node in arrays[0].findall('item')] if arrays else []
        check(len(arrays) == 1 and actual == keywords and len(set(actual)) == len(actual)
              and all(actual), 'Android ' + locale + ' ordered scoreboard keywords match')
    if 'values' in translations and 'values-en' in translations:
        check(set(translations['values']) == set(translations['values-en']),
              'Android scoreboard Chinese and English string keys match')
        check(all(len(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value)) == len(placeholders(value))
                  and placeholders(value) == placeholders(translations['values-en'][name])
                  for name, value in translations['values'].items()),
              'Android scoreboard bilingual format placeholders match')


def check_android_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'ScoreboardToolDefinition.kt')
    definition = read_optional(definition_path) or ''
    fields = (
        ('code', r'code\s*=\s*"scoreboard"'),
        ('name', r'name\s*=\s*resources\.getString\(R\.string\.scoreboard_name\)'),
        ('description', r'description\s*=\s*resources\.getString\(R\.string\.scoreboard_description\)'),
        ('category', r'category\s*=\s*ToolCategory\.LIFE'),
        ('mode', r'mode\s*=\s*ToolMode\.LOCAL'),
        ('icon', r'icon\s*=\s*"scoreboard"'),
        ('keywords', r'keywords\s*=\s*resources\.getStringArray\(R\.array\.scoreboard_keywords\)\.toList\(\)'),
        ('sort', r'sortOrder\s*=\s*624\b'),
        ('featured', r'isFeatured\s*=\s*false\b'),
    )
    for field, pattern in fields:
        check(len(re.findall(pattern, definition)) == 1, 'Android scoreboard ToolDefinition ' + field)
    defaults = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/model/ToolMetadata.kt')) or ''
    for field, default in (('requiresLogin', 'false'), ('status', 'ToolStatus.ENABLED'), ('version', '1')):
        value = re.search(r'\b' + field + r'\s*=\s*([^,\n]+)', definition)
        check((value.group(1).strip() == default if value else re.search(
            r'val\s+' + field + r'\s*:\s*\w+\s*=\s*' + re.escape(default) + r'\s*,', defaults) is not None),
            'Android scoreboard ' + field + ' agrees with catalog defaults')
    check(re.search(r'override\s+val\s+requiredPermissions\s*:\s*List<String>\s*=\s*emptyList\(\)',
                    definition) is not None, 'Android scoreboard declares no extra runtime permissions')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] * len(re.findall(r'\bcode\s*=\s*"scoreboard"',
                                                                          read_source(path))))
    check(owners == [os.path.normpath(definition_path)],
          'Android scoreboard code has exactly one ToolDefinition owner')
    container = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt')) or ''
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and 'ToolRegistry(' in registry.group(0)
          and len(re.findall(r'\bScoreboardToolDefinition\s*\(', registry.group(0))) == 1,
          'Android bundled ToolRegistry registers scoreboard exactly once')
    icons = read_optional(os.path.join(JAVA_ROOT,
                                       'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt')) or ''
    check(len(re.findall(r'"scoreboard"\s*->', icons)) == 1 and re.search(
        r'"scoreboard"\s*->\s*R\.drawable\.ic_scoreboard\b', icons) is not None,
        'Android scoreboard has one explicit icon mapping')
    icon_path = os.path.join(RES_ROOT, 'drawable/ic_scoreboard.xml')
    icon = ET.parse(icon_path).getroot() if os.path.isfile(icon_path) else None
    android = '{http://schemas.android.com/apk/res/android}'
    check(icon is not None and icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
          and icon.get(android + 'height') == '24dp'
          and icon.get(android + 'viewportWidth') == '24' and icon.get(android + 'viewportHeight') == '24'
          and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
          'Android scoreboard ships a nonempty 24dp vector icon')


def check_domain_features(check):
    domain_path = os.path.join(FEATURE_ROOT, 'domain/ScoreboardModels.kt')
    domain = read_optional(domain_path)
    check(domain is not None, 'Android scoreboard domain model exists')
    if domain is None:
        return
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'scoreboard domain provides ' + label)


def check_candidate_isolation(check):
    script = read_optional(os.path.join(ROOT, 'scripts/check_scoreboard_runtime.py')) or ''
    harness = read_optional(os.path.join(ROOT, 'scripts/check_admin_statistics_runtime.py')) or ''
    check('strict_isolation=True' in script,
          'scoreboard candidate opts into credential-stripped isolation')
    check('codex-stats-redis-' in harness and "'redis-server', '--save', '', '--appendonly', 'no'" in harness,
          'strict candidate uses disposable Redis instead of production credentials')
    check('exposed_network = network if strict_isolation else edge' in harness,
          'strict candidate stays off the production edge network')
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
    print('Scoreboard offline static acceptance:', checks[0] - len(failures), '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='72' AND success=1") == '1',
          'V72 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V72 creates one scoreboard row')
    check(CODE in records(), 'public LIFE catalog includes scoreboard')
    check_tool_metadata(check, records()[CODE], 'public scoreboard', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin scoreboard detail', defaults)
    check_tool_metadata(check, listing, 'admin scoreboard list', defaults)
    check(detail == listing, 'admin scoreboard detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers the enabled LIFE category')

    edited = dict(defaults,
                  name=u'记分牌验收',
                  description=u'后台编辑的记分牌说明。',
                  icon=CODE + '_admin_check',
                  keywords=[u'验收', 'scoreboard-admin'],
                  sortOrder=625,
                  version=2,
                  featured=True,
                  requiresLogin=True,
                  configJson='{"acceptance":"scoreboard"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited scoreboard metadata', edited)
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE) == '0'
          and sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE) == '0',
          'fresh scoreboard starts with zero usage')
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')

    queries = [
        "SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
        "SELECT * FROM flyway_schema_history WHERE version='72'",
        "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
        "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE,
    ]
    snapshot = [sql(query) for query in queries]
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V72 replay preserves operator edits, configuration, timestamps and usage')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V72 replay keeps one scoreboard row')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin),
                        'seed replay preserves edited scoreboard', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored scoreboard metadata', defaults)

    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'scoreboard increments daily and all-time usage')
    beijing_daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily "
                        "WHERE tool_code='%s' AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE)
    check(beijing_daily == '2', 'scoreboard daily opens use the Beijing date')
    popular_rows = call('tools/popular?limit=50')
    check(len(popular_rows) <= 50 and len(set(row['code'] for row in popular_rows)) == len(popular_rows)
          and all(row['useCount'] >= 1 for row in popular_rows),
          'popular tools are bounded, unique and used')
    popular = dict((row['code'], row) for row in popular_rows).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'scoreboard participates in popularity')

    stats = {}
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        stats[days] = data
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            groups = [row for row in data[group] if row['code'] == group_code]
            check(len(groups) == 1 and groups[0]['count'] >= 1 and groups[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes scoreboard usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2
              and ranks[0]['name'] == CONTRACT['name'] and ranks[0]['status'] == 'ENABLED',
              'scoreboard participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_scoreboard_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
