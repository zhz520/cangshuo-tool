# -*- coding: utf-8 -*-
"""V79 anniversary_countdown static and isolated backend acceptance."""
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
CODE = 'anniversary_countdown'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=anniversary_countdown&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V79__seed_anniversary_countdown_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/anniversary')
RESOURCE_NAME = re.compile(r'^anniversary_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [u'纪念日', u'生日', u'周年', u'倒数', u'重要日子', u'倒数日',
               'anniversary', 'birthday', 'countdown']
KEYWORDS_EN = ['anniversary', 'birthday', 'countdown', 'important date', 'yearly',
               u'纪念日', u'生日', u'周年', u'倒数']
CONTRACT = {
    'code': CODE,
    'name': u'纪念日',
    'description': u'在本机记录生日、相识、家庭与重要日子，支持每月或每年重复、置顶、倒计时和农历显示。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 631,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Anniversaries',
    'description': 'Keep birthdays, relationships, family dates and important days locally with monthly or yearly repeats, pinning, countdowns and lunar dates.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('local event model', 'data class AnniversaryEvent('),
    ('500 event budget', 'const val MAX_EVENTS = 500'),
    ('title budget', 'const val MAX_TITLE_CODE_POINTS = 80'),
    ('notes budget', 'const val MAX_NOTES_CODE_POINTS = 500'),
    ('date bounds', 'val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)'),
    ('repeat rules', 'enum class AnniversaryRepeat'),
    ('categories', 'enum class AnniversaryCategory'),
    ('filters', 'enum class AnniversaryFilter { ALL, UPCOMING, TODAY, PAST }'),
    ('sorting', 'enum class AnniversarySort { PINNED, NEXT_DATE, NEWEST, NAME }'),
    ('title normalization', 'fun normalizeTitle(title: String): String'),
    ('leap-day rule', 'original.monthValue == 2 && original.dayOfMonth == 29'),
    ('month-end clamp', 'month.atDay(minOf(original.dayOfMonth, month.lengthOfMonth()))'),
    ('next occurrence', 'fun nextOccurrence(event: AnniversaryEvent, todayEpochDay: Long): LocalDate'),
    ('past detection', 'fun isPast(event: AnniversaryEvent, todayEpochDay: Long): Boolean'),
    ('three future dates', 'fun upcomingDates(event: AnniversaryEvent, todayEpochDay: Long, count: Int = 3)'),
    ('elapsed years', 'fun elapsedYears(event: AnniversaryEvent, todayEpochDay: Long): AnniversaryElapsed?'),
    ('search filter sort', 'fun filterAndSort('),
    ('statistics', 'fun stats(events: List<AnniversaryEvent>, todayEpochDay: Long): AnniversaryStats'),
    ('use cases', 'class AnniversaryUseCases'),
)
UI_FEATURES = (
    ('search field', 'R.string.anniversary_search'),
    ('four filters', 'AnniversaryFilter.PAST'),
    ('four sorts', 'AnniversarySort.NAME'),
    ('statistics card', 'AnniversaryStatsCard('),
    ('new and edit dialog', 'AnniversaryEditorDialog('),
    ('native date bounds', 'dialog.datePicker.minDate = AnniversaryPolicy.MIN_DATE'),
    ('category controls', 'AnniversaryCategory.entries.forEach'),
    ('repeat controls', 'AnniversaryRepeat.entries.forEach'),
    ('pin control', 'ToolboxSwitch(checked = pinned'),
    ('detail dialog', 'AnniversaryDetailDialog('),
    ('three future dates', 'AnniversaryPolicy.upcomingDates('),
    ('delete confirmation', 'R.string.anniversary_delete_title'),
    ('keyboard-safe dialog', 'safeDrawingPadding().imePadding()'),
    ('full-height editor', '.heightIn(max = 760.dp).fillMaxHeight()'),
    ('scrollable dialog body', '.weight(1f).verticalScroll(rememberScrollState())'),
    ('compact keyboard actions', 'val compactHeight = maxHeight < 480.dp'),
    ('adaptive filters', 'FlowRow('),
    ('date button semantics', 'R.string.anniversary_choose_date'),
)


def check_seed(check):
    source = read_source(SEED_PATH)
    check(source is not None, 'V79 catalog seed exists')
    migration_dir = os.path.dirname(SEED_PATH)
    files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V79__'))
    check(files == [os.path.basename(SEED_PATH)], 'V79 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured',
    ], 'V79 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'], 'V79 metadata and ordered icons match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V79 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*631\s*,\s*0\s*\)", body)
          is not None, 'V79 defaults and sort match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V79 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V79 replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_resources(check):
    feature = ''
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                feature += '\n' + read_source(os.path.join(current, name))
    references = set(re.findall(r'R\.string\.(anniversary_[A-Za-z0-9_]+)', feature))
    plural_references = set(re.findall(r'R\.plurals\.(anniversary_[A-Za-z0-9_]+)', feature))
    arrays = set(re.findall(r'R\.array\.(anniversary_[A-Za-z0-9_]+)', feature))
    check({'anniversary_name', 'anniversary_description'}.issubset(references),
          'anniversary metadata resources are referenced')
    check(arrays == {'anniversary_keywords'}, 'anniversary keyword array is referenced')
    check(plural_references == {'anniversary_days_away', 'anniversary_days_past', 'anniversary_elapsed_years'},
          'anniversary plural resources are referenced')
    translations = {}
    plural_translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        resources = ET.fromstring(read_source(os.path.join(RES_ROOT, locale, 'strings.xml')).encode('utf-8'))
        nodes = [node for node in resources.findall('string') if RESOURCE_NAME.match(node.get('name', ''))]
        names = [node.get('name') for node in nodes]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in nodes)
        plural_nodes = [node for node in resources.findall('plurals') if RESOURCE_NAME.match(node.get('name', ''))]
        plural_translations[locale] = dict(
            (node.get('name'),
             dict((item.get('quantity'), ''.join(item.itertext())) for item in node.findall('item')))
            for node in plural_nodes
        )
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in nodes),
              'Android ' + locale + ' anniversary strings are unique and nonempty')
        check(set(translations[locale]) == references,
              'Android ' + locale + ' anniversary resource references are exact')
        check(set(plural_translations[locale]) == plural_references,
              'Android ' + locale + ' anniversary plural references are exact')
        check(all(set(values) == {'one', 'other'} and all(values.values())
                  for values in plural_translations[locale].values()),
              'Android ' + locale + ' anniversary plurals contain one and other')
        check(translations[locale].get('anniversary_name') == expected['name']
              and translations[locale].get('anniversary_description') == expected['description'],
              'Android ' + locale + ' anniversary metadata matches')
        keyword_nodes = resources.findall("string-array[@name='anniversary_keywords']")
        actual = [(node.text or '').strip() for node in keyword_nodes[0].findall('item')] if keyword_nodes else []
        check(len(keyword_nodes) == 1 and actual == keywords,
              'Android ' + locale + ' anniversary keywords match')
        category_keys = ('birthday', 'relationship', 'family', 'work', 'other')
        repeat_keys = ('none', 'monthly', 'yearly')
        actual_categories = [translations[locale].get('anniversary_category_' + key, '')
                             for key in category_keys]
        actual_repeats = [translations[locale].get('anniversary_repeat_' + key, '') for key in repeat_keys]
        check(len(actual_categories) == 5 and all(actual_categories),
              'Android ' + locale + ' has five anniversary categories')
        check(len(actual_repeats) == 3 and all(actual_repeats),
              'Android ' + locale + ' has three anniversary repeat labels')
    check(set(translations['values']) == set(translations['values-en']), 'anniversary bilingual keys match')
    check(all(placeholders(value) == placeholders(translations['values-en'][name])
              for name, value in translations['values'].items()), 'anniversary bilingual placeholders match')
    check(set(plural_translations['values']) == set(plural_translations['values-en']),
          'anniversary bilingual plural keys match')
    check(all(placeholders(quantity_value) == placeholders(plural_translations['values-en'][name][quantity])
              for name, values in plural_translations['values'].items()
              for quantity, quantity_value in values.items()),
          'anniversary bilingual plural placeholders match')


def check_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'AnniversaryToolDefinition.kt')
    definition = read_source(definition_path)
    for label, token in (
        ('code', 'code = "anniversary_countdown"'),
        ('name', 'R.string.anniversary_name'),
        ('description', 'R.string.anniversary_description'),
        ('category', 'ToolCategory.LIFE'),
        ('mode', 'ToolMode.LOCAL'),
        ('icon', 'icon = "anniversary_countdown"'),
        ('keywords', 'R.array.anniversary_keywords'),
        ('sort', 'sortOrder = 631'),
        ('featured', 'isFeatured = false'),
    ):
        check(token in definition, 'Anniversary ToolDefinition ' + label)
    check('requiredPermissions: List<String> = emptyList()' in definition,
          'Anniversary declares no runtime permissions')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] *
                              len(re.findall(r'\bcode\s*=\s*"anniversary_countdown"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)],
          'Anniversary has exactly one ToolDefinition owner')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and len(re.findall(r'\bAnniversaryToolDefinition\s*\(', registry.group(0))) == 1,
          'Anniversary is registered exactly once')
    icons = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(re.search(r'"anniversary_countdown"\s*->\s*R\.drawable\.ic_anniversary\b', icons) is not None,
          'Anniversary has one icon mapping')
    icon = ET.parse(os.path.join(RES_ROOT, 'drawable/ic_anniversary.xml')).getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
          and icon.get(android + 'height') == '24dp'
          and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
          'Anniversary ships a nonempty 24dp vector icon')


def check_database(check):
    database = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabase.kt'))
    check('AnniversaryEventEntity::class' in database and 'version = 10' in database
          and 'abstract fun anniversaryEventDao(): AnniversaryEventDao' in database,
          'Room exports anniversaries at schema version 10')
    migration = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabaseMigrations.kt'))
    check('val MIGRATION_9_10 = object : Migration(9, 10)' in migration,
          'Room declares 9 to 10 migration')
    for token in ('CREATE TABLE IF NOT EXISTS anniversary_event', 'date_epoch_day INTEGER NOT NULL',
                  'repeat_rule INTEGER NOT NULL', 'category INTEGER NOT NULL', 'pinned INTEGER NOT NULL',
                  'index_anniversary_event_pinned', 'index_anniversary_event_date_epoch_day'):
        check(token in migration, 'Room migration contains ' + token)
    schema_path = os.path.join(
        ROOT, 'android/app/schemas/com.cangshuo.toolbox.core.database.ToolboxDatabase/10.json')
    schema = json.loads(read_source(schema_path))
    check(schema['database']['version'] == 10, 'Room schema 10 exported')
    entities = dict((row['tableName'], row) for row in schema['database']['entities'])
    check('anniversary_event' in entities, 'Room schema contains anniversary_event')
    event = entities['anniversary_event']
    check(event['primaryKey'] == {'autoGenerate': True, 'columnNames': ['id']},
          'Anniversary uses generated id')
    check(set(row['columnName'] for row in event['fields']) == {
        'id', 'title', 'notes', 'date_epoch_day', 'repeat_rule', 'category', 'pinned',
        'created_at', 'updated_at',
    }, 'Anniversary columns match migration')
    check(set(tuple(index['columnNames']) for index in event.get('indices', [])) == {
        ('pinned',), ('date_epoch_day',),
    }, 'Anniversary exports both indexes')
    dao = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/AnniversaryEventDao.kt'))
    check('fun observeAll(): Flow<List<AnniversaryEventEntity>>' in dao
          and 'suspend fun insertIfBelowLimit(' in dao
          and 'WHERE (SELECT COUNT(*) FROM anniversary_event) < :limit' in dao
          and 'suspend fun update(' in dao and 'suspend fun delete(' in dao
          and 'suspend fun setPinned(' in dao,
          'Anniversary DAO exposes bounded atomic writes and all list actions')


def check_implementation(check):
    domain = read_source(os.path.join(FEATURE_ROOT, 'domain/AnniversaryModels.kt'))
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'anniversary domain provides ' + label)
    repository = read_source(os.path.join(FEATURE_ROOT, 'data/RoomAnniversaryRepository.kt'))
    for label, token in (
        ('domain observation mapping', 'dao.observeAll().map'),
        ('atomic limit exception', 'if (id == -1L) throw AnniversaryLimitException()'),
        ('lunar calendar mapping', 'Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar'),
        ('device local date', 'override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()'),
    ):
        check(token in repository, 'anniversary repository provides ' + label)
    view_model = read_source(os.path.join(FEATURE_ROOT, 'ui/AnniversaryViewModel.kt'))
    for token in ('saved["anniversaryFilter"]', 'saved["anniversarySort"]', 'saved["anniversaryQuery"]'):
        check(token in view_model, 'anniversary ViewModel persists ' + token)
    check('if (mutable.value.saving) return false' in view_model,
          'anniversary ViewModel prevents duplicate saves')
    check('AnniversaryPolicy.MAX_EVENTS' in view_model
          and 'AnniversaryLimitException' in view_model,
          'anniversary ViewModel handles limit before and during atomic insert')
    check('setBusy(event.id, true)' in view_model and 'setBusy(event.id, false)' in view_model,
          'anniversary ViewModel prevents duplicate pin writes')
    route = read_source(os.path.join(FEATURE_ROOT, 'ui/AnniversaryRoute.kt'))
    check('repeatOnLifecycle(Lifecycle.State.RESUMED)' in route and 'delay(60_000L)' in route,
          'anniversary refreshes the device date while resumed')
    screen = read_source(os.path.join(FEATURE_ROOT, 'ui/AnniversaryScreen.kt'))
    for label, token in UI_FEATURES:
        check(token in screen, 'anniversary UI provides ' + label)
    manifest = read_source(os.path.join(ROOT, 'android/app/src/main/AndroidManifest.xml'))
    check('android:windowSoftInputMode="adjustResize"' in manifest,
          'Activity resizes above software keyboard')


def check_candidate_isolation(check):
    script = read_source(os.path.join(ROOT, 'scripts/check_anniversary_countdown_runtime.py'))
    harness = read_source(os.path.join(ROOT, 'scripts/check_admin_statistics_runtime.py'))
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'anniversary candidate opts into credential-stripped isolation')
    check("'redis-server', '--save', '', '--appendonly', 'no'" in harness
          and 'temporary_network = ' in harness,
          'strict candidate uses disposable services and network')


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
    print('Anniversary countdown offline static acceptance:', checks[0], '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='79' AND success=1") == '1',
          'V79 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V79 creates one anniversary row')
    check(CODE in records(), 'public LIFE catalog includes anniversary_countdown')
    check_tool_metadata(check, records()[CODE], 'public anniversary countdown', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin anniversary detail', defaults)
    check_tool_metadata(check, listing, 'admin anniversary list', defaults)
    check(detail == listing, 'admin anniversary detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers enabled LIFE category')

    edited = dict(defaults, name=u'纪念日验收', description=u'后台编辑的纪念日说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'anniversary-admin'],
                  sortOrder=632, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"anniversary_countdown"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited anniversary metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='79'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V79 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin),
                        'seed replay preserves edited anniversary', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored anniversary metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'anniversary increments daily and all-time usage')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE) == '2',
          'anniversary daily opens use the Beijing date')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'anniversary participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes anniversary usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'anniversary participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_anniversary_countdown_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
