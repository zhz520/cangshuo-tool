# -*- coding: utf-8 -*-
"""V84 period_tracker static and isolated backend acceptance."""
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
CODE = 'period_tracker'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=period_tracker&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V84__seed_period_tracker_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/period')
RESOURCE_NAME = re.compile(r'^period_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [u'经期', u'月经', u'周期', u'排卵', u'日历', u'健康', 'period', 'cycle']
KEYWORDS_EN = ['period', 'menstruation', 'cycle', 'calendar', 'symptoms', 'health', u'经期', u'月经']
CONTRACT = {
    'code': CODE,
    'name': u'经期记录',
    'description': u'在本机记录经期起止、流量、症状和情绪，查看日历、历史统计与周期估算。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 636,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Period tracker',
    'description': 'Track period dates, flow, symptoms and mood locally, with calendar history and cycle estimates.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('independent flow levels', 'enum class PeriodFlow'),
    ('typed symptom bitmask', 'enum class PeriodSymptom'),
    ('typed mood', 'enum class PeriodMood'),
    ('cycle range model', 'data class PeriodCycle('),
    ('ongoing range', 'val endEpochDay: Long?'),
    ('settings defaults', 'data class PeriodSettings('),
    ('validated start/end ranges', 'if (!validDate(start) || start > todayEpochDay)'),
    ('overlap rejection', 'if (overlaps) return PeriodValidation.Invalid(PeriodError.OVERLAP)'),
    ('1200-record limit', 'const val MAX_CYCLES = 1_200'),
    ('500-codepoint notes', 'const val MAX_NOTES_CODE_POINTS = 500'),
    ('15-60 cycle filter', 'filter { it in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS }'),
    ('1-14 period filter', 'filter { it in MIN_PERIOD_DAYS..MAX_PERIOD_DAYS }'),
    ('latest twelve cycles', 'takeLast(12)'),
    ('next period estimate', 'val nextStart = last?.let { it.startEpochDay + averageCycle }'),
    ('14-day luteal estimate', 'const val LUTEAL_DAYS = 14'),
    ('five-before one-after window', 'fertileStartEpochDay = ovulation?.minus(5L)'),
    ('fixed 42-day calendar', '(0 until 42).map { offset ->'),
    ('actual and predicted calendar states', 'predictedPeriod = predictedStart != null'),
)
IMPLEMENTATION_FEATURES = (
    ('Room entity', 'data class PeriodCycleEntity('),
    ('unique start index', 'Index(value = ["start_epoch_day"], unique = true)'),
    ('updated index', 'Index(value = ["updated_at"])'),
    ('transactional limit', 'if (count() >= limit) return -1L'),
    ('checked update', 'check(dao.update(toEntity(cycle)) > 0)'),
    ('checked delete', 'check(dao.delete(id) > 0)'),
    ('settings persistence', 'getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)'),
    ('calendar refresh from Room', 'combine(useCases.observeAll(), useCases.observeSettings())'),
    ('saved month', 'saved["periodMonth"] = month.toString()'),
    ('saved selected day', 'saved["periodSelectedEpochDay"]'),
    ('midnight refresh', 'delay(60_000L)'),
    ('message revision', 'messageRevision = mutable.value.messageRevision + 1'),
)
UI_FEATURES = (
    ('cycle overview', 'PeriodOverview('),
    ('six-week calendar', 'PeriodCalendar('),
    ('calendar semantics', 'contentDescription = description'),
    ('selected state semantics', 'this.selected = day.selected'),
    ('status semantics', 'stateDescription = fullStatus'),
    ('minimum date-cell width', 'maxOf(48.dp, (availableWidth - gap * 6) / 7)'),
    ('non-color status shapes', 'MarkerRing('),
    ('notes marker', 'MarkerBar('),
    ('distinct note diamond', 'MarkerDiamond('),
    ('legend marker samples', 'LegendItem('),
    ('selected day detail', 'SelectedDayDetail('),
    ('history rows', 'CycleRow('),
    ('loading state', 'ToolboxLoadingState('),
    ('snackbar host', 'SnackbarHost('),
    ('date pickers', 'DatePickerDialog('),
    ('flow chips', 'PeriodFlow.entries'),
    ('symptom chips', 'PeriodSymptom.entries'),
    ('mood chips', 'PeriodMood.entries'),
    ('ongoing switch', 'period_ongoing_switch'),
    ('notes input', 'R.string.period_notes'),
    ('settings dialog', 'PeriodSettingsDialog('),
    ('delete confirmation', 'DeleteConfirmationDialog('),
    ('wide dialog override', 'DialogProperties(usePlatformDefaultWidth = false)'),
    ('responsive wrapping', 'FlowRow('),
    ('IME safety', 'imePadding()'),
    ('shared primary button', 'ToolboxButton('),
    ('shared outlined button', 'ToolboxOutlinedButton('),
)


def check_seed(check):
    source = read_source(SEED_PATH)
    check(source is not None, 'V84 catalog seed exists')
    files = sorted(name for name in os.listdir(os.path.dirname(SEED_PATH)) if name.startswith('V84__'))
    check(files == [os.path.basename(SEED_PATH)], 'V84 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured',
    ], 'V84 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'], 'V84 metadata and ordered values match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V84 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*636\s*,\s*0\s*\)", body)
          is not None, 'V84 defaults and sort match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V84 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V84 replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_resources(check):
    feature = ''
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                feature += '\n' + read_source(os.path.join(current, name))
    string_refs = set(re.findall(r'R\.string\.(period_[A-Za-z0-9_]+)', feature))
    plural_refs = set(re.findall(r'R\.plurals\.(period_[A-Za-z0-9_]+)', feature))
    arrays = set(re.findall(r'R\.array\.(period_[A-Za-z0-9_]+)', feature))
    check({'period_name', 'period_description', 'period_save', 'period_delete'}.issubset(string_refs),
          'Period metadata and primary controls are referenced')
    check(plural_refs == {'period_day_value', 'period_late_days', 'period_record_count'},
          'Period plurals are referenced')
    check(arrays == {'period_keywords', 'period_weekdays', 'period_weekdays_full'}, 'Period arrays are referenced')
    translations = {}
    plurals = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        resources = ET.fromstring(read_source(os.path.join(RES_ROOT, locale, 'strings.xml')).encode('utf-8'))
        nodes = [node for node in resources.findall('string') if RESOURCE_NAME.match(node.get('name', ''))]
        names = [node.get('name') for node in nodes]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in nodes)
        plural_nodes = [node for node in resources.findall('plurals') if RESOURCE_NAME.match(node.get('name', ''))]
        plurals[locale] = dict(
            (node.get('name'), dict((item.get('quantity'), ''.join(item.itertext())) for item in node.findall('item')))
            for node in plural_nodes
        )
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in nodes),
              'Android ' + locale + ' period strings are unique and nonempty')
        check(set(translations[locale]) == string_refs, 'Android ' + locale + ' period string references are exact')
        check(set(plurals[locale]) == plural_refs, 'Android ' + locale + ' period plural references are exact')
        check(translations[locale].get('period_name') == expected['name']
              and translations[locale].get('period_description') == expected['description'],
              'Android ' + locale + ' period metadata matches')
        keyword_nodes = resources.findall("string-array[@name='period_keywords']")
        actual = [(node.text or '').strip() for node in keyword_nodes[0].findall('item')] if keyword_nodes else []
        check(len(keyword_nodes) == 1 and actual == keywords, 'Android ' + locale + ' period keywords match')
        weekday_nodes = resources.findall("string-array[@name='period_weekdays']")
        check(len(weekday_nodes) == 1 and len(weekday_nodes[0].findall('item')) == 7,
              'Android ' + locale + ' period weekdays have seven entries')
        full_weekday_nodes = resources.findall("string-array[@name='period_weekdays_full']")
        check(len(full_weekday_nodes) == 1 and len(full_weekday_nodes[0].findall('item')) == 7,
              'Android ' + locale + ' full weekdays have seven entries')
        for name, quantities in plurals[locale].items():
            check(set(quantities) == {'one', 'other'} and all(quantities.values()),
                  'Android ' + locale + ' ' + name + ' has both plural quantities')
    check(set(translations['values']) == set(translations['values-en']), 'Period bilingual string keys match')
    check(set(plurals['values']) == set(plurals['values-en']), 'Period bilingual plural keys match')
    check(all(placeholders(value) == placeholders(translations['values-en'][name])
              for name, value in translations['values'].items()), 'Period bilingual placeholders match')
    check(all(placeholders(quantity) == placeholders(plurals['values-en'][name][kind])
              for name, values in plurals['values'].items() for kind, quantity in values.items()),
          'Period bilingual plural placeholders match')


def check_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'PeriodTrackerToolDefinition.kt')
    definition = read_source(definition_path)
    for label, token in (
        ('code', 'code = "period_tracker"'), ('name', 'R.string.period_name'),
        ('description', 'R.string.period_description'), ('category', 'ToolCategory.LIFE'),
        ('mode', 'ToolMode.LOCAL'), ('icon', 'icon = "period_tracker"'),
        ('keywords', 'R.array.period_keywords'), ('sort', 'sortOrder = 636'),
        ('featured', 'isFeatured = false'),
    ):
        check(token in definition, 'Period ToolDefinition ' + label)
    check('requiredPermissions: List<String> = emptyList()' in definition,
          'Period requests no runtime permission')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] *
                              len(re.findall(r'\bcode\s*=\s*"period_tracker"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)], 'Period has exactly one ToolDefinition owner')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and len(re.findall(r'\bPeriodTrackerToolDefinition\s*\(', registry.group(0))) == 1,
          'Period is registered exactly once')
    icons = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(re.search(r'"period_tracker"\s*->\s*R\.drawable\.ic_period_tracker\b', icons) is not None,
          'Period icon mapping exists')
    android = '{http://schemas.android.com/apk/res/android}'
    icon = ET.parse(os.path.join(RES_ROOT, 'drawable/ic_period_tracker.xml')).getroot()
    check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
          and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
          'Period icon is a nonempty 24dp vector')


def check_database(check):
    implementation = ''
    for name in ('PeriodCycleEntity.kt', 'PeriodCycleDao.kt'):
        implementation += '\n' + read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database', name))
    for label, token in IMPLEMENTATION_FEATURES[:4]:
        check(token in implementation, 'Period database provides ' + label)
    database = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabase.kt'))
    check('PeriodCycleEntity::class' in database and 'version = 14' in database
          and 'abstract fun periodCycleDao(): PeriodCycleDao' in database,
          'Period database registers entity, DAO and version 14')
    migration = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabaseMigrations.kt'))
    check('val MIGRATION_13_14 = object : Migration(13, 14)' in migration and
          'CREATE TABLE IF NOT EXISTS period_cycle' in migration and
          'index_period_cycle_start_epoch_day' in migration and 'index_period_cycle_updated_at' in migration,
          'Period migration creates table and indexes')
    provider = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabaseProvider.kt'))
    check('MIGRATION_13_14' in provider, 'Period migration is registered')
    schema_path = os.path.join(ROOT, 'android/app/schemas/com.cangshuo.toolbox.core.database.ToolboxDatabase/14.json')
    schema = json.loads(read_source(schema_path))
    check(schema['database']['version'] == 14, 'Room schema 14 exported')
    entities = dict((row['tableName'], row) for row in schema['database']['entities'])
    check('period_cycle' in entities, 'Room schema contains period_cycle')
    table = entities['period_cycle']
    check(set(row['columnName'] for row in table['fields']) == {
        'id', 'start_epoch_day', 'end_epoch_day', 'flow', 'symptoms_mask', 'mood', 'notes',
        'created_at', 'updated_at',
    }, 'Period columns match migration')
    indices = dict((index['name'], index) for index in table.get('indices', []))
    check(indices.get('index_period_cycle_start_epoch_day', {}).get('unique') is True,
          'Period start date is unique')
    check('index_period_cycle_updated_at' in indices, 'Period updated_at is indexed')
    check('PERIOD' not in read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/sync/domain/CloudSync.kt')),
          'Period data is not enrolled in cloud sync')


def check_implementation(check):
    domain = read_source(os.path.join(FEATURE_ROOT, 'domain/PeriodModels.kt'))
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'Period domain provides ' + label)
    for name in ('data/RoomPeriodRepository.kt', 'data/LocalPeriodSettingsRepository.kt',
                 'ui/PeriodViewModel.kt', 'ui/PeriodRoute.kt'):
        implementation = read_source(os.path.join(FEATURE_ROOT, name))
        check(implementation is not None, 'Period implementation exists: ' + name)
    combined = ''
    for name in ('data/RoomPeriodRepository.kt', 'data/LocalPeriodSettingsRepository.kt',
                 'ui/PeriodViewModel.kt', 'ui/PeriodRoute.kt'):
        combined += '\n' + read_source(os.path.join(FEATURE_ROOT, name))
    for label, token in IMPLEMENTATION_FEATURES[4:]:
        check(token in combined or token in read_source(os.path.join(FEATURE_ROOT, 'domain/PeriodModels.kt')),
              'Period implementation provides ' + label)
    screen = read_source(os.path.join(FEATURE_ROOT, 'ui/PeriodScreen.kt'))
    for label, token in UI_FEATURES:
        check(token in screen, 'Period UI provides ' + label)
    for token in ('MaterialTheme.colorScheme', 'MaterialTheme.shapes', 'ToolboxDimens.pageGutter',
                  'ToolboxDimens.formMaxWidth', 'safeDrawingPadding()'):
        check(token in screen, 'Period UI uses shared token ' + token)
    check('#' not in screen, 'Period UI contains no hard-coded color literal')
    tests = []
    test_root = os.path.join(ROOT, 'android/app/src/test/java/com/cangshuo/toolbox/feature/period')
    for current, unused, names in os.walk(test_root):
        tests.extend(name for name in names if name.endswith('Test.kt'))
    check(sorted(tests) == ['PeriodPolicyTest.kt', 'PeriodViewModelTest.kt'],
          'Period has policy and ViewModel test suites')
    docs = read_source(os.path.join(ROOT, 'docs/PERIOD_TRACKER.md'))
    check(docs is not None and 'EmmaTellblom/Mensinator' in docs and 'lekhanpro/cyclecare' in docs
          and 'f79504fbe61e43f4d6dc5b17e0f6040d342e18a5' in docs
          and '8ea15d10dfe5dafa6cd3206774098ff1ca13a0e5' in docs,
          'Period documents fixed open-source reviews')
    privacy = read_source(os.path.join(ROOT, 'docs/PRIVACY_POLICY.md'))
    check(privacy is not None and u'经期记录' in privacy and u'流量、症状、情绪、备注' in privacy,
          'Privacy policy covers local period health data')


def check_candidate_isolation(check):
    script = read_source(os.path.join(ROOT, 'scripts/check_period_tracker_runtime.py'))
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'Period candidate opts into credential-stripped isolation')


def check_static(check):
    check_seed(check)
    check_resources(check)
    check_registration(check)
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
    print('Period tracker offline static acceptance:', checks[0], '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='84' AND success=1") == '1', 'V84 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V84 creates one period row')
    check(CODE in records(), 'public LIFE catalog includes period_tracker')
    check_tool_metadata(check, records()[CODE], 'public period tracker', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin period detail', defaults)
    check_tool_metadata(check, listing, 'admin period list', defaults)
    check(detail == listing, 'admin period detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers enabled LIFE category')
    edited = dict(defaults, name=u'经期记录验收', description=u'后台编辑的经期记录说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'period-admin'],
                  sortOrder=637, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"period_tracker"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited period metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='84'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot, 'V84 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'seed replay preserves edited period', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored period metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'Period increments daily and all-time usage')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'Period participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes period usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'Period participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_period_tracker_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
