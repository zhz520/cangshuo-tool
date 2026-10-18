# -*- coding: utf-8 -*-
"""V85 countdown_timer static and isolated backend acceptance."""
from __future__ import print_function
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'countdown_timer'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=countdown_timer&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V85__seed_countdown_timer_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/timer')
RESOURCE_NAME = re.compile(r'^countdown_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [u'计时器', u'倒计时', u'计时', u'提醒', u'番茄', 'timer', 'countdown', 'alarm']
KEYWORDS_EN = ['timer', 'countdown', 'alarm', 'reminder', 'pomodoro', u'计时器', u'倒计时', u'计时']
CONTRACT = {
    'code': CODE,
    'name': u'计时器',
    'description': u'设置最长 24 小时的倒计时，支持暂停、增加时间、通知控制和本机状态恢复。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 637,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Timer',
    'description': 'Set a countdown up to 24 hours with pause, add-time, notification controls and local state recovery.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('four timer states', 'enum class CountdownStatus'),
    ('monotonic deadline state', 'val deadlineElapsedMillis: Long?'),
    ('wall deadline recovery', 'val deadlineWallMillis: Long?'),
    ('deadline remaining calculation', 'val deadline = deadlineElapsedMillis'),
    ('progress calculation', 'return (1f - remainingAt(nowElapsedMillis).toFloat() / totalMillis.toFloat())'),
    ('one second minimum', 'const val MIN_DURATION_MILLIS = 1_000L'),
    ('23:59:59 maximum', 'const val MAX_DURATION_MILLIS = (23L * 3_600L + 59L * 60L + 59L) * 1_000L'),
    ('nine presets', 'val presetsMinutes = listOf(1, 3, 5, 10, 15, 25, 30, 45, 60)'),
    ('pause freezes remaining', 'remainingMillis = current.remainingAt(nowElapsedMillis)'),
    ('resume recreates deadline', 'deadlineElapsedMillis = nowElapsedMillis + remaining'),
    ('add minute semantics', 'val total = current.totalMillis + addition'),
    ('finished clears deadline', 'status = CountdownStatus.FINISHED'),
)
SERVICE_FEATURES = (
    ('foreground before work', 'startForeground(ONGOING_ID, buildOngoing(current))'),
    ('special use foreground type', 'android:foregroundServiceType="specialUse"'),
    ('countdown foreground subtype', 'android:value="countdown timer"'),
    ('partial wake lock', 'PowerManager.PARTIAL_WAKE_LOCK'),
    ('wake lock permission', 'android.permission.WAKE_LOCK'),
    ('sticky restart', 'return START_STICKY'),
    ('pause action', 'ACTION_PAUSE'),
    ('resume action', 'ACTION_RESUME'),
    ('add minute action', 'ACTION_ADD_MINUTE'),
    ('reset stops without starting foreground', 'context.stopService('),
    ('ongoing notification', 'setOngoing(true)'),
    ('only alert once', 'setOnlyAlertOnce(true)'),
    ('progress category', 'Notification.CATEGORY_PROGRESS'),
    ('alarm category', 'Notification.CATEGORY_ALARM'),
    ('completion channel', 'COMPLETION_CHANNEL'),
    ('wake lock release', 'releaseWakeLock()'),
    ('single foreground transition', 'if (!foregrounded)'),
    ('active session policy', 'CountdownPolicy.requiresForegroundService(current)'),
)
IMPLEMENTATION_FEATURES = (
    ('boot count detection', 'Settings.Global.BOOT_COUNT'),
    ('same boot monotonic restore', 'storedDeadlineElapsedMillis - nowElapsedMillis'),
    ('reboot wall restore', 'storedDeadlineWallMillis?.let { (it - nowWallMillis)'),
    ('synchronous state writes', 'preferences.edit(commit = true)'),
    ('start service action', 'CountdownTimerService.start(appContext)'),
    ('reset service stop', 'CountdownTimerService.stop(appContext)'),
    ('saved time fields', 'saved["timerHours"]'),
    ('saved label', 'saved["timerLabel"]'),
    ('message revision', 'messageRevision = mutable.value.messageRevision + 1'),
)
UI_FEATURES = (
    ('large progress ring', 'CircularProgressIndicator('),
    ('clock display', 'CountdownPolicy.format(state.remainingMillis)'),
    ('quick presets', 'CountdownPolicy.presetsMinutes.forEach'),
    ('custom hours', 'R.string.countdown_hours'),
    ('custom minutes', 'R.string.countdown_minutes_label'),
    ('custom seconds', 'R.string.countdown_seconds'),
    ('name input', 'R.string.countdown_label'),
    ('notification permission card', 'TimerPermissionCard('),
    ('start pause resume', 'R.string.countdown_resume'),
    ('extend action', 'R.string.countdown_add_minute'),
    ('reset confirmation', 'TimerResetDialog('),
    ('keep screen switch', 'ToolboxSwitch('),
    ('responsive wrapping', 'FlowRow('),
    ('large text uses full-width inputs', 'largeText -> maxWidth'),
    ('large text stacks card header', 'val stackHeader = maxWidth < 300.dp'),
    ('IME safety', 'imePadding()'),
    ('shared prominent button', 'ToolboxButton('),
    ('shared secondary button', 'ToolboxOutlinedButton('),
)


def check_seed(check):
    source = read_source(SEED_PATH)
    check(source is not None, 'V85 catalog seed exists')
    files = sorted(name for name in os.listdir(os.path.dirname(SEED_PATH)) if name.startswith('V85__'))
    check(files == [os.path.basename(SEED_PATH)], 'V85 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured',
    ], 'V85 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'], 'V85 metadata and ordered values match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V85 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*637\s*,\s*0\s*\)", body)
          is not None, 'V85 defaults and sort match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V85 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V85 replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_resources(check):
    feature = ''
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                feature += '\n' + read_source(os.path.join(current, name))
    string_refs = set(re.findall(r'R\.string\.(countdown_[A-Za-z0-9_]+)', feature))
    arrays = set(re.findall(r'R\.array\.(countdown_[A-Za-z0-9_]+)', feature))
    check({'countdown_name', 'countdown_description', 'countdown_start', 'countdown_reset'}.issubset(string_refs),
          'Countdown metadata and primary controls are referenced')
    check(arrays == {'countdown_keywords'}, 'Countdown keyword array is referenced')
    translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        resources = ET.fromstring(read_source(os.path.join(RES_ROOT, locale, 'strings.xml')).encode('utf-8'))
        nodes = [node for node in resources.findall('string') if RESOURCE_NAME.match(node.get('name', ''))]
        names = [node.get('name') for node in nodes]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in nodes)
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in nodes),
              'Android ' + locale + ' countdown strings are unique and nonempty')
        check(set(translations[locale]) == string_refs,
              'Android ' + locale + ' countdown string references are exact')
        check(translations[locale].get('countdown_name') == expected['name']
              and translations[locale].get('countdown_description') == expected['description'],
              'Android ' + locale + ' countdown metadata matches')
        keyword_nodes = resources.findall("string-array[@name='countdown_keywords']")
        actual = [(node.text or '').strip() for node in keyword_nodes[0].findall('item')] if keyword_nodes else []
        check(len(keyword_nodes) == 1 and actual == keywords, 'Android ' + locale + ' countdown keywords match')
    check(set(translations['values']) == set(translations['values-en']), 'Countdown bilingual string keys match')
    check(all(placeholders(value) == placeholders(translations['values-en'][name])
              for name, value in translations['values'].items()), 'Countdown bilingual placeholders match')


def check_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'CountdownTimerToolDefinition.kt')
    definition = read_source(definition_path)
    for label, token in (
        ('code', 'code = "countdown_timer"'), ('name', 'R.string.countdown_name'),
        ('description', 'R.string.countdown_description'), ('category', 'ToolCategory.LIFE'),
        ('mode', 'ToolMode.LOCAL'), ('icon', 'icon = "countdown_timer"'),
        ('keywords', 'R.array.countdown_keywords'), ('sort', 'sortOrder = 637'),
        ('featured', 'isFeatured = false'),
    ):
        check(token in definition, 'Countdown ToolDefinition ' + label)
    check('requiredPermissions: List<String> = emptyList()' in definition,
          'Countdown requests no opening runtime permission')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] *
                              len(re.findall(r'\bcode\s*=\s*"countdown_timer"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)], 'Countdown has exactly one ToolDefinition owner')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and len(re.findall(r'\bCountdownTimerToolDefinition\s*\(', registry.group(0))) == 1,
          'Countdown is registered exactly once')
    icons = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(re.search(r'"countdown_timer"\s*->\s*R\.drawable\.ic_timer\b', icons) is not None,
          'Countdown has one icon mapping')
    android = '{http://schemas.android.com/apk/res/android}'
    for path in ('drawable/ic_timer.xml', 'drawable/ic_timer_notification.xml'):
        icon = ET.parse(os.path.join(RES_ROOT, path)).getroot()
        check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
              and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
              path + ' is a nonempty 24dp vector')


def check_implementation(check):
    domain = read_source(os.path.join(FEATURE_ROOT, 'domain/TimerModels.kt'))
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'Countdown domain provides ' + label)
    combined = '\n' + domain
    for name in ('data/CountdownTimerStore.kt', 'data/LocalCountdownRepository.kt',
                 'ui/TimerViewModel.kt', 'ui/TimerRoute.kt'):
        combined += '\n' + read_source(os.path.join(FEATURE_ROOT, name))
    for label, token in IMPLEMENTATION_FEATURES:
        check(token in combined, 'Countdown implementation provides ' + label)
    service = read_source(os.path.join(FEATURE_ROOT, 'service/CountdownTimerService.kt'))
    manifest = read_source(os.path.join(ROOT, 'android/app/src/main/AndroidManifest.xml'))
    for label, token in SERVICE_FEATURES:
        check(token in service or token in manifest, 'Countdown service provides ' + label)
    check('ACTION_STOP' not in service, 'Countdown has no foreground-started stop command')
    check('context.startService(' not in service, 'Countdown service entries always use foreground starts')
    check(service.index('startForeground(ONGOING_ID') < service.index('when (intent?.action)'),
          'Countdown foreground precedes command handling')
    check('Settings.Global.BOOT_COUNT' in combined and 'deadline_wall_millis' in combined,
          'Countdown restores across process and reboot clocks')
    database = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabase.kt'))
    check('version = 14' in database and 'Countdown' not in database,
          'Countdown adds no Room schema change')
    release_gate = read_source(os.path.join(ROOT, 'scripts/check_release_artifacts.py'))
    check('android.permission.WAKE_LOCK' in release_gate,
          'Release permission gate includes timer wake lock')
    screen = read_source(os.path.join(FEATURE_ROOT, 'ui/TimerScreen.kt'))
    for label, token in UI_FEATURES:
        check(token in screen, 'Countdown UI provides ' + label)
    for token in ('MaterialTheme.colorScheme', 'MaterialTheme.shapes', 'ToolboxDimens.formMaxWidth',
                  'LiveRegionMode.Assertive'):
        check(token in screen, 'Countdown UI uses shared token ' + token)
    check('#' not in screen, 'Countdown UI contains no hard-coded color literal')
    tests = []
    test_root = os.path.join(ROOT, 'android/app/src/test/java/com/cangshuo/toolbox/feature/timer')
    for current, unused, names in os.walk(test_root):
        tests.extend(name for name in names if name.endswith('Test.kt'))
    check(sorted(tests) == ['TimerPolicyTest.kt', 'TimerViewModelTest.kt'],
          'Countdown has policy and ViewModel test suites')
    docs = read_source(os.path.join(ROOT, 'docs/COUNTDOWN_TIMER.md'))
    check(docs is not None and 'ashpakouski/QuickTimer' in docs and 'dubrowgn/micro-timer' in docs
          and '2eea3ab03766633df5b0944f67dcb37b5b642264' in docs
          and '9d05e0cbddb737706377311e27560e6e6905d2cb' in docs,
          'Countdown documents fixed open-source reviews')


def check_candidate_isolation(check):
    script = read_source(os.path.join(ROOT, 'scripts/check_countdown_timer_runtime.py'))
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'Countdown candidate opts into credential-stripped isolation')


def check_static(check):
    check_seed(check)
    check_resources(check)
    check_registration(check)
    check_implementation(check)
    check_candidate_isolation(check)


def offline_acceptance():
    checks = [0]

    def check(condition, label):
        if not condition:
            raise AssertionError(label)
        checks[0] += 1
        print('PASS', checks[0], label)

    check_static(check)
    print('Countdown timer offline static acceptance:', checks[0], '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='85' AND success=1") == '1', 'V85 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V85 creates one countdown row')
    check(CODE in records(), 'public LIFE catalog includes countdown_timer')
    check_tool_metadata(check, records()[CODE], 'public countdown timer', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin countdown detail', defaults)
    check_tool_metadata(check, listing, 'admin countdown list', defaults)
    check(detail == listing, 'admin countdown detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers enabled LIFE category')
    edited = dict(defaults, name=u'计时器验收', description=u'后台编辑的计时器说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'countdown-admin'],
                  sortOrder=638, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"countdown_timer"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited countdown metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='85'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot, 'V85 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'seed replay preserves edited countdown', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored countdown metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'Countdown increments daily and all-time usage')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'Countdown participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes countdown usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'Countdown participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_countdown_timer_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
