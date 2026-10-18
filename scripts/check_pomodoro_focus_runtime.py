# -*- coding: utf-8 -*-
"""V80 pomodoro_focus static and isolated backend acceptance."""
from __future__ import print_function
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'pomodoro_focus'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=pomodoro_focus&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V80__seed_pomodoro_focus_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/pomodoro')
RESOURCE_NAME = re.compile(r'^pomodoro_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [u'番茄', u'专注', u'计时', u'休息', u'效率', 'pomodoro', 'focus', 'timer']
KEYWORDS_EN = ['pomodoro', 'focus', 'timer', 'break', 'productivity', u'番茄', u'专注', u'计时']
CONTRACT = {
    'code': CODE,
    'name': u'番茄专注',
    'description': u'用专注与休息循环管理时间，支持自定义时长、自动切换、后台计时和本地专注统计。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 632,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Focus Timer',
    'description': 'Work and rest in repeatable focus cycles with custom durations, automatic transitions, background timing and local focus statistics.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('focus phases', 'enum class PomodoroPhase'),
    ('duration bounds', 'const val MAX_FOCUS_MINUTES = 120'),
    ('round bounds', 'const val MIN_ROUNDS = 2'),
    ('history budget', 'const val MAX_HISTORY = 2_000'),
    ('catch-up bound', 'const val MAX_CATCH_UP_TRANSITIONS = 12'),
    ('setting validation', 'fun validateSettings(settings: PomodoroSettings): PomodoroError?'),
    ('label validation', 'fun validateLabel(label: String): PomodoroError?'),
    ('deadline based clock', 'fun remainingAt(nowMillis: Long): Int'),
    ('transition engine', 'fun tick(state: PomodoroTimerState, settings: PomodoroSettings, now: Long): PomodoroAdvance'),
    ('auto-start transitions', 'safe.autoStartBreaks'),
    ('manual skip', 'fun skip(state: PomodoroTimerState, settings: PomodoroSettings, now: Long): PomodoroAdvance'),
    ('manual reset', 'fun reset(state: PomodoroTimerState, settings: PomodoroSettings, now: Long): PomodoroAdvance'),
    ('phase switch', 'fun switchPhase('),
    ('statistics', 'fun stats(sessions: List<PomodoroSession>, todayEpochDay: Long, zone: ZoneId): PomodoroStats'),
    ('clock format', 'fun formatClock(seconds: Int): String'),
    ('use cases', 'class PomodoroUseCases'),
)
SERVICE_FEATURES = (
    ('foreground service', 'class PomodoroTimerService : Service()'),
    ('ongoing notification', 'setOngoing(true)'),
    ('two channels', 'ONGOING_CHANNEL'),
    ('pause resume action', 'ACTION_TOGGLE'),
    ('skip action', 'ACTION_SKIP'),
    ('completion notification', 'postCompletionNotification'),
    ('immediate foreground', 'startForeground(ONGOING_NOTIFICATION_ID'),
    ('survives task removal', 'return START_STICKY'),
    ('notification tap', 'PendingIntent.getActivity'),
)
UI_FEATURES = (
    ('phase selector', 'PomodoroPhase.entries.forEach'),
    ('circular progress', 'CircularProgressIndicator('),
    ('timer controls', 'onStartOrPause'),
    ('label editing', 'PomodoroLabelDialog('),
    ('notification permission', 'Manifest.permission.POST_NOTIFICATIONS'),
    ('three presets', 'PomodoroSettings(25, 5, 15, 4)'),
    ('focus duration field', 'R.string.pomodoro_focus_minutes'),
    ('short break field', 'R.string.pomodoro_short_minutes'),
    ('long break field', 'R.string.pomodoro_long_minutes'),
    ('round setting', 'R.string.pomodoro_rounds'),
    ('auto start switches', 'SettingSwitchRow('),
    ('local statistics', 'PomodoroStatsCard('),
    ('lifetime totals', 'pomodoro_total_focuses'),
    ('history list', 'PomodoroHistoryRow('),
    ('reset confirmation', 'R.string.pomodoro_reset_title'),
    ('clear confirmation', 'R.string.pomodoro_clear_history_title'),
    ('adaptive controls', 'FlowRow('),
)


def check_seed(check):
    source = read_source(SEED_PATH)
    check(source is not None, 'V80 catalog seed exists')
    migration_dir = os.path.dirname(SEED_PATH)
    files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V80__'))
    check(files == [os.path.basename(SEED_PATH)], 'V80 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured',
    ], 'V80 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'], 'V80 metadata and ordered icons match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V80 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*632\s*,\s*0\s*\)", body)
          is not None, 'V80 defaults and sort match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V80 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V80 replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_resources(check):
    feature = ''
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                feature += '\n' + read_source(os.path.join(current, name))
    references = set(re.findall(r'R\.string\.(pomodoro_[A-Za-z0-9_]+)', feature))
    plurals = set(re.findall(r'R\.plurals\.(pomodoro_[A-Za-z0-9_]+)', feature))
    arrays = set(re.findall(r'R\.array\.(pomodoro_[A-Za-z0-9_]+)', feature))
    check({'pomodoro_name', 'pomodoro_description', 'pomodoro_pause', 'pomodoro_resume'}.issubset(references),
          'Pomodoro metadata and controls are referenced')
    check(plurals == {'pomodoro_days'}, 'Pomodoro plural resources are referenced')
    check(arrays == {'pomodoro_keywords'}, 'Pomodoro keyword array is referenced')
    translations = {}
    plural_translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        resources = ET.fromstring(read_source(os.path.join(RES_ROOT, locale, 'strings.xml')).encode('utf-8'))
        nodes = [node for node in resources.findall('string') if RESOURCE_NAME.match(node.get('name', ''))]
        names = [node.get('name') for node in nodes]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in nodes)
        plural_nodes = [node for node in resources.findall('plurals') if RESOURCE_NAME.match(node.get('name', ''))]
        plural_translations[locale] = dict(
            (node.get('name'), dict((item.get('quantity'), ''.join(item.itertext())) for item in node.findall('item')))
            for node in plural_nodes
        )
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in nodes),
              'Android ' + locale + ' Pomodoro strings are unique and nonempty')
        check(set(translations[locale]) == references,
              'Android ' + locale + ' Pomodoro resource references are exact')
        check(set(plural_translations[locale]) == plurals,
              'Android ' + locale + ' Pomodoro plural references are exact')
        check(translations[locale].get('pomodoro_name') == expected['name']
              and translations[locale].get('pomodoro_description') == expected['description'],
              'Android ' + locale + ' Pomodoro metadata matches')
        keyword_nodes = resources.findall("string-array[@name='pomodoro_keywords']")
        actual = [(node.text or '').strip() for node in keyword_nodes[0].findall('item')] if keyword_nodes else []
        check(len(keyword_nodes) == 1 and actual == keywords,
              'Android ' + locale + ' Pomodoro keywords match')
        check(all(set(values) == {'one', 'other'} and all(values.values())
                  for values in plural_translations[locale].values()),
              'Android ' + locale + ' Pomodoro plurals have both quantities')
    check(set(translations['values']) == set(translations['values-en']), 'Pomodoro bilingual keys match')
    check(all(placeholders(value) == placeholders(translations['values-en'][name])
              for name, value in translations['values'].items()), 'Pomodoro bilingual placeholders match')
    check(all(placeholders(quantity_value) == placeholders(plural_translations['values-en'][name][quantity])
              for name, values in plural_translations['values'].items()
              for quantity, quantity_value in values.items()),
          'Pomodoro bilingual plural placeholders match')


def check_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'PomodoroToolDefinition.kt')
    definition = read_source(definition_path)
    for label, token in (
        ('code', 'code = "pomodoro_focus"'),
        ('name', 'R.string.pomodoro_name'),
        ('description', 'R.string.pomodoro_description'),
        ('category', 'ToolCategory.LIFE'),
        ('mode', 'ToolMode.LOCAL'),
        ('icon', 'icon = "pomodoro_focus"'),
        ('keywords', 'R.array.pomodoro_keywords'),
        ('sort', 'sortOrder = 632'),
        ('featured', 'isFeatured = false'),
    ):
        check(token in definition, 'Pomodoro ToolDefinition ' + label)
    check('requiredPermissions: List<String> = emptyList()' in definition,
          'Pomodoro requests notification permission lazily')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] *
                              len(re.findall(r'\bcode\s*=\s*"pomodoro_focus"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)], 'Pomodoro has exactly one ToolDefinition owner')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and len(re.findall(r'\bPomodoroToolDefinition\s*\(', registry.group(0))) == 1,
          'Pomodoro is registered exactly once')
    icons = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(re.search(r'"pomodoro_focus"\s*->\s*R\.drawable\.ic_pomodoro\b', icons) is not None,
          'Pomodoro has one icon mapping')
    for icon_path, label in (('drawable/ic_pomodoro.xml', 'tool'), ('drawable/ic_pomodoro_notification.xml', 'notification')):
        icon = ET.parse(os.path.join(RES_ROOT, icon_path)).getroot()
        android = '{http://schemas.android.com/apk/res/android}'
        check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
              and icon.get(android + 'height') == '24dp'
              and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
              'Pomodoro ' + label + ' icon is a nonempty 24dp vector')


def check_service(check):
    service = read_source(os.path.join(FEATURE_ROOT, 'service/PomodoroTimerService.kt'))
    for label, token in SERVICE_FEATURES:
        check(token in service, 'Pomodoro service provides ' + label)
    manifest = read_source(os.path.join(ROOT, 'android/app/src/main/AndroidManifest.xml'))
    for token in ('android.permission.FOREGROUND_SERVICE',
                  'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
                  'android.permission.POST_NOTIFICATIONS',
                  '.feature.pomodoro.service.PomodoroTimerService',
                  'android:foregroundServiceType="specialUse"',
                  'PROPERTY_SPECIAL_USE_FGS_SUBTYPE'):
        check(token in manifest, 'Android manifest contains ' + token)


def check_database(check):
    database = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabase.kt'))
    check('PomodoroSessionEntity::class' in database and 'version = 11' in database
          and 'abstract fun pomodoroSessionDao(): PomodoroSessionDao' in database,
          'Room exports Pomodoro history at schema version 11')
    migration = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabaseMigrations.kt'))
    check('val MIGRATION_10_11 = object : Migration(10, 11)' in migration,
          'Room declares 10 to 11 migration')
    for token in ('CREATE TABLE IF NOT EXISTS pomodoro_session', 'planned_seconds INTEGER NOT NULL',
                  'elapsed_seconds INTEGER NOT NULL', 'completed INTEGER NOT NULL',
                  'index_pomodoro_session_ended_at', 'index_pomodoro_session_phase'):
        check(token in migration, 'Room migration contains ' + token)
    schema_path = os.path.join(ROOT, 'android/app/schemas/com.cangshuo.toolbox.core.database.ToolboxDatabase/11.json')
    schema = json.loads(read_source(schema_path))
    check(schema['database']['version'] == 11, 'Room schema 11 exported')
    entities = dict((row['tableName'], row) for row in schema['database']['entities'])
    check('pomodoro_session' in entities, 'Room schema contains pomodoro_session')
    event = entities['pomodoro_session']
    check(event['primaryKey'] == {'autoGenerate': True, 'columnNames': ['id']},
          'Pomodoro history uses generated id')
    check(set(row['columnName'] for row in event['fields']) == {
        'id', 'label', 'phase', 'planned_seconds', 'elapsed_seconds', 'completed', 'started_at', 'ended_at',
    }, 'Pomodoro columns match migration')
    check(set(tuple(index['columnNames']) for index in event.get('indices', [])) == {
        ('ended_at',), ('phase',),
    }, 'Pomodoro exports both indexes')
    dao = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/PomodoroSessionDao.kt'))
    check('fun observeRecent(): Flow<List<PomodoroSessionEntity>>' in dao
          and 'suspend fun insertAndTrim(' in dao and 'ORDER BY ended_at DESC, id DESC LIMIT :limit' in dao
          and 'suspend fun clear(): Int' in dao,
          'Pomodoro DAO observes bounded history with atomic trim and clear')


def check_implementation(check):
    domain = read_source(os.path.join(FEATURE_ROOT, 'domain/PomodoroModels.kt'))
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'Pomodoro domain provides ' + label)
    repository = read_source(os.path.join(FEATURE_ROOT, 'data/RoomPomodoroRepository.kt'))
    for label, token in (
        ('mutex serialization', 'private val mutex = Mutex()'),
        ('history mapping', 'dao.observeRecent().map'),
        ('bounded inserts', 'PomodoroPolicy.MAX_HISTORY'),
        ('service start', 'PomodoroTimerService.start(context)'),
        ('device local day', 'LocalDate.now().toEpochDay()'),
    ):
        check(token in repository, 'Pomodoro repository provides ' + label)
    store = read_source(os.path.join(FEATURE_ROOT, 'data/PomodoroTimerStore.kt'))
    for label, token in (
        ('deadline persistence', 'KEY_DEADLINE'),
        ('session-start persistence', 'KEY_SESSION_STARTED'),
        ('settings persistence', 'KEY_AUTO_START_FOCUS'),
        ('bounded KTX edit', 'preferences.edit {'),
    ):
        check(token in store, 'Pomodoro timer store provides ' + label)
    view_model = read_source(os.path.join(FEATURE_ROOT, 'ui/PomodoroViewModel.kt'))
    check('combine(' in view_model and 'useCases.tick()' in view_model,
          'Pomodoro ViewModel observes state and advances the deadline')
    check('if (mutable.value.busy) return' in view_model,
          'Pomodoro ViewModel rejects duplicate actions')
    screen = read_source(os.path.join(FEATURE_ROOT, 'ui/PomodoroScreen.kt'))
    for label, token in UI_FEATURES:
        check(token in screen, 'Pomodoro UI provides ' + label)
    check('ActivityResultContracts.RequestPermission()' in screen and 'startAfterPermission' in screen,
          'Pomodoro requests notification permission before the first start')
    manifest = read_source(os.path.join(ROOT, 'android/app/src/main/AndroidManifest.xml'))
    check('android:windowSoftInputMode="adjustResize"' in manifest,
          'Activity resizes above software keyboard')


def check_candidate_isolation(check):
    script = read_source(os.path.join(ROOT, 'scripts/check_pomodoro_focus_runtime.py'))
    harness = read_source(os.path.join(ROOT, 'scripts/check_admin_statistics_runtime.py'))
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'Pomodoro candidate opts into credential-stripped isolation')
    check("'redis-server', '--save', '', '--appendonly', 'no'" in harness
          and 'temporary_network = ' in harness,
          'strict candidate uses disposable services and network')


def check_static(check):
    check_seed(check)
    check_resources(check)
    check_registration(check)
    check_service(check)
    check_database(check)
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
    print('Pomodoro focus offline static acceptance:', checks[0], '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='80' AND success=1") == '1', 'V80 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V80 creates one Pomodoro row')
    check(CODE in records(), 'public LIFE catalog includes pomodoro_focus')
    check_tool_metadata(check, records()[CODE], 'public Pomodoro focus', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin Pomodoro detail', defaults)
    check_tool_metadata(check, listing, 'admin Pomodoro list', defaults)
    check(detail == listing, 'admin Pomodoro detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers enabled LIFE category')

    edited = dict(defaults, name=u'番茄专注验收', description=u'后台编辑的番茄专注说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'pomodoro-admin'],
                  sortOrder=633, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"pomodoro_focus"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited Pomodoro metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='80'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot, 'V80 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'seed replay preserves edited Pomodoro', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored Pomodoro metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'Pomodoro increments daily and all-time usage')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'Pomodoro participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes Pomodoro usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'Pomodoro participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_pomodoro_focus_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
