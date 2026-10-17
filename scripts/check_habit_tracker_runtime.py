# -*- coding: utf-8 -*-
"""V78 habit_tracker static and isolated backend acceptance (Python 2/3)."""
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
CODE = 'habit_tracker'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=habit_tracker&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V78__seed_habit_tracker_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/habit')
RESOURCE_NAME = re.compile(r'^habit_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [u'习惯', u'打卡', u'连续', u'坚持', u'目标', u'日程', 'habit', 'streak', 'routine']
KEYWORDS_EN = ['habit', 'check-in', 'streak', 'routine', 'goal', 'schedule', u'习惯', u'打卡', u'连续']
CONTRACT = {
    'code': CODE,
    'name': u'习惯打卡',
    'description': u'在本机建立习惯并按星期安排打卡，查看连续天数、完成率与最近 28 天记录。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 630,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Habits',
    'description': 'Build local habits with weekday schedules, streaks, completion rates and 28-day history.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('habit limit', 'const val MAX_HABITS = 100'),
    ('entry limit', 'const val MAX_ENTRIES = 10_000'),
    ('name budget', 'const val MAX_NAME_CODE_POINTS = 80'),
    ('description budget', 'const val MAX_DESCRIPTION_CODE_POINTS = 500'),
    ('seven weekday mask', 'const val ALL_DAYS_MASK = (1 shl 7) - 1'),
    ('name validation', 'fun validateName(name: String): HabitError?'),
    ('weekday validation', 'fun validateWeekdays(mask: Int): HabitError?'),
    ('scheduled day', 'fun isScheduled(mask: Int, epochDay: Long): Boolean'),
    ('current streak', 'fun currentStreak(habit: Habit, todayEpochDay: Long): Int'),
    ('best streak', 'fun bestStreak(habit: Habit): Int'),
    ('week progress', 'fun weekProgress(habit: Habit, todayEpochDay: Long): HabitWeekProgress'),
    ('completion rate', 'fun completionRate(habit: Habit, todayEpochDay: Long, days: Int = 30): Int'),
    ('28-day history', 'fun history(habit: Habit, todayEpochDay: Long, days: Int = HISTORY_DAYS): List<HabitDay>'),
    ('filtering and sorting', 'fun filterAndSort('),
    ('statistics', 'fun stats(habits: List<Habit>, todayEpochDay: Long): HabitStats'),
    ('use cases', 'class HabitUseCases'),
)
UI_FEATURES = (
    ('search field', 'R.string.habit_search'),
    ('status filters', 'HabitFilter.ARCHIVED'),
    ('four sort controls', 'HabitSort.NAME'),
    ('today check-in', 'onCheckedChange = { onToggleToday() }'),
    ('weekly strip', 'HabitWeekStrip('),
    ('edit dialog', 'HabitEditorDialog('),
    ('28-day detail history', 'HabitHistoryGrid('),
    ('keyboard-safe editor', 'Modifier.fillMaxSize().safeDrawingPadding().imePadding()'),
    ('scrollable editor body', '.weight(1f, fill = false).verticalScroll(rememberScrollState())'),
    ('adaptive statistics', 'HabitStat(stringResource(R.string.habit_stat_active), state.stats.active, Modifier.weight(1f))'),
    ('wrap-safe controls', 'FlowRow('),
    ('delete confirmation', 'R.string.habit_delete_title'),
    ('history cleanup confirmation', 'R.string.habit_clear_history_title'),
)


def check_seed(check):
    source = read_source(SEED_PATH)
    check(source is not None, 'V78 catalog seed exists')
    migration_dir = os.path.dirname(SEED_PATH)
    files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V78__'))
    check(files == [os.path.basename(SEED_PATH)], 'V78 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V78 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'], 'V78 metadata and ordered icons match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V78 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*630\s*,\s*0\s*\)", body)
          is not None, 'V78 defaults and sort match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V78 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V78 replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_resources(check):
    feature = ''
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                feature += '\n' + read_source(os.path.join(current, name))
    references = set(re.findall(r'R\.string\.(habit_[A-Za-z0-9_]+)', feature))
    plural_references = set(re.findall(r'R\.plurals\.(habit_[A-Za-z0-9_]+)', feature))
    arrays = set(re.findall(r'R\.array\.(habit_[A-Za-z0-9_]+)', feature))
    check({'habit_name', 'habit_description'}.issubset(references), 'habit metadata resources are referenced')
    check(arrays == {'habit_keywords', 'habit_weekdays'}, 'habit keyword and weekday arrays are referenced')
    check(plural_references == {'habit_current_streak', 'habit_best_streak', 'habit_checked_total', 'habit_schedule_summary'},
          'habit plural resources are referenced')
    translations = {}
    plural_translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        resources = ET.fromstring(read_source(os.path.join(RES_ROOT, locale, 'strings.xml')).encode('utf-8'))
        nodes = [node for node in resources.findall('string') if RESOURCE_NAME.match(node.get('name', ''))]
        names = [node.get('name') for node in nodes]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in nodes)
        plural_nodes = [node for node in resources.findall('plurals') if RESOURCE_NAME.match(node.get('name', ''))]
        plural_translations[locale] = dict((node.get('name'), dict((item.get('quantity'), ''.join(item.itertext())) for item in node.findall('item'))) for node in plural_nodes)
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in nodes),
              'Android ' + locale + ' habit strings are unique and nonempty')
        check(set(translations[locale]) == references, 'Android ' + locale + ' habit resource references are exact')
        check(set(plural_translations[locale]) == plural_references, 'Android ' + locale + ' habit plural references are exact')
        check(all(set(values) == {'one', 'other'} and all(values.values())
                  for values in plural_translations[locale].values()),
              'Android ' + locale + ' habit plurals contain one and other')
        check(translations[locale].get('habit_name') == expected['name']
              and translations[locale].get('habit_description') == expected['description'],
              'Android ' + locale + ' habit metadata matches')
        keyword_nodes = resources.findall("string-array[@name='habit_keywords']")
        actual = [(node.text or '').strip() for node in keyword_nodes[0].findall('item')] if keyword_nodes else []
        check(len(keyword_nodes) == 1 and actual == keywords, 'Android ' + locale + ' habit keywords match')
        weekday_nodes = resources.findall("string-array[@name='habit_weekdays']")
        weekdays = [(node.text or '').strip() for node in weekday_nodes[0].findall('item')] if weekday_nodes else []
        check(len(weekday_nodes) == 1 and len(weekdays) == 7 and all(weekdays),
              'Android ' + locale + ' weekday labels contain seven values')
    check(set(translations['values']) == set(translations['values-en']), 'habit bilingual keys match')
    check(all(placeholders(value) == placeholders(translations['values-en'][name])
              for name, value in translations['values'].items()), 'habit bilingual placeholders match')
    check(set(plural_translations['values']) == set(plural_translations['values-en']), 'habit bilingual plural keys match')
    check(all(placeholders(quantity_value) == placeholders(plural_translations['values-en'][name][quantity])
              for name, values in plural_translations['values'].items()
              for quantity, quantity_value in values.items()),
          'habit bilingual plural placeholders match')


def check_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'HabitToolDefinition.kt')
    definition = read_source(definition_path)
    for label, token in (
        ('code', 'code = "habit_tracker"'), ('name', 'R.string.habit_name'),
        ('description', 'R.string.habit_description'), ('category', 'ToolCategory.LIFE'),
        ('mode', 'ToolMode.LOCAL'), ('icon', 'icon = "habit_tracker"'),
        ('keywords', 'R.array.habit_keywords'), ('sort', 'sortOrder = 630'),
        ('featured', 'isFeatured = false'),
    ):
        check(token in definition, 'Habit ToolDefinition ' + label)
    check('requiredPermissions: List<String> = emptyList()' in definition, 'Habit declares no runtime permissions')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] * len(re.findall(r'\bcode\s*=\s*"habit_tracker"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)], 'Habit has exactly one ToolDefinition owner')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and len(re.findall(r'\bHabitToolDefinition\s*\(', registry.group(0))) == 1,
          'Habit is registered exactly once')
    icons = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(re.search(r'"habit_tracker"\s*->\s*R\.drawable\.ic_habit_tracker\b', icons) is not None,
          'Habit has one icon mapping')
    icon = ET.parse(os.path.join(RES_ROOT, 'drawable/ic_habit_tracker.xml')).getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
          and icon.get(android + 'height') == '24dp' and any(
              node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
          'Habit ships a nonempty 24dp vector icon')


def check_database(check):
    database = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabase.kt'))
    check('HabitEntity::class' in database and 'HabitEntryEntity::class' in database and 'version = 9' in database
          and 'abstract fun habitDao(): HabitDao' in database, 'Room exports habits at schema version 9')
    migration = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabaseMigrations.kt'))
    check('val MIGRATION_8_9 = object : Migration(8, 9)' in migration, 'Room declares 8 to 9 migration')
    for token in ('CREATE TABLE IF NOT EXISTS habit', 'weekdays_mask INTEGER', 'archived_day INTEGER', 'created_day INTEGER',
                  'CREATE TABLE IF NOT EXISTS habit_entry', 'PRIMARY KEY(habit_id, date_epoch_day)',
                  'ON DELETE CASCADE', 'index_habit_entry_date_epoch_day'):
        check(token in migration, 'Room migration contains ' + token)
    schema = json.loads(read_source(os.path.join(ROOT, 'android/app/schemas/com.cangshuo.toolbox.core.database.ToolboxDatabase/9.json')))
    check(schema['database']['version'] == 9, 'Room schema 9 exported')
    entities = dict((row['tableName'], row) for row in schema['database']['entities'])
    check('habit' in entities and 'habit_entry' in entities, 'Room schema contains both habit tables')
    habit = entities['habit']
    entry = entities['habit_entry']
    check(habit['primaryKey'] == {'autoGenerate': True, 'columnNames': ['id']}, 'Habit uses generated id')
    check(set(row['columnName'] for row in habit['fields']) == {
        'id', 'name', 'description', 'weekdays_mask', 'archived', 'archived_day', 'position', 'created_day', 'created_at', 'updated_at',
    }, 'Habit columns match migration')
    check(entry['primaryKey'] == {'autoGenerate': False, 'columnNames': ['habit_id', 'date_epoch_day']},
          'Habit entry uses habit/date composite key')
    check(len(entry.get('foreignKeys', [])) == 1 and entry['foreignKeys'][0]['onDelete'] == 'CASCADE',
          'Habit entry cascades with habit deletion')
    dao = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/HabitDao.kt'))
    check('fun observeAll(): Flow<List<HabitEntity>>' in dao and 'fun observeEntries(): Flow<List<HabitEntryEntity>>' in dao
          and 'suspend fun insertIfBelowLimit(' in dao and 'suspend fun insertEntryIfAllowed(' in dao,
          'Habit DAO exposes bounded atomic inserts')


def check_implementation(check):
    domain = read_source(os.path.join(FEATURE_ROOT, 'domain/HabitModels.kt'))
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'habit domain provides ' + label)
    repository = read_source(os.path.join(FEATURE_ROOT, 'data/RoomHabitRepository.kt'))
    for label, token in (
        ('combined observation', 'combine(dao.observeAll(), dao.observeEntries())'),
        ('domain mapping', 'private fun toDomain(habit: HabitEntity, entries: List<HabitEntryEntity>)'),
        ('entity mapping', 'private fun toEntity(habit: Habit)'),
        ('local date', 'override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()'),
    ):
        check(token in repository, 'habit repository provides ' + label)
    view_model = read_source(os.path.join(FEATURE_ROOT, 'ui/HabitViewModel.kt'))
    for token in ('saved["habitFilter"]', 'saved["habitSort"]', 'saved["habitQuery"]'):
        check(token in view_model, 'habit ViewModel persists ' + token)
    check('if (id == null && allHabits.size >= HabitPolicy.MAX_HABITS)' in view_model,
          'habit ViewModel enforces 100 habit limit')
    check('useCases.addEntry(habit.id, dateEpochDay)' in view_model
          and 'HabitMessage.ENTRY_LIMIT' in view_model,
          'habit ViewModel handles atomic check-in limit result')
    check('setBusy(habit.id, true)' in view_model and 'setBusy(habit.id, false)' in view_model,
          'habit ViewModel prevents duplicate per-habit writes')
    screen = read_source(os.path.join(FEATURE_ROOT, 'ui/HabitScreen.kt'))
    for label, token in UI_FEATURES:
        check(token in screen, 'habit UI provides ' + label)
    manifest = read_source(os.path.join(ROOT, 'android/app/src/main/AndroidManifest.xml'))
    check('android:windowSoftInputMode="adjustResize"' in manifest, 'Activity resizes above software keyboard')


def check_candidate_isolation(check):
    script = read_source(os.path.join(ROOT, 'scripts/check_habit_tracker_runtime.py'))
    harness = read_source(os.path.join(ROOT, 'scripts/check_admin_statistics_runtime.py'))
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'habit candidate opts into credential-stripped isolation')
    check("'redis-server', '--save', '', '--appendonly', 'no'" in harness
          and 'temporary_network = ' in harness, 'strict candidate uses disposable services and network')


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
    print('Habit tracker offline static acceptance:', checks[0], '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='78' AND success=1") == '1', 'V78 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1', 'V78 creates one habit row')
    check(CODE in records(), 'public LIFE catalog includes habit_tracker')
    check_tool_metadata(check, records()[CODE], 'public habit tracker', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin habit detail', defaults)
    check_tool_metadata(check, listing, 'admin habit list', defaults)
    check(detail == listing, 'admin habit detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers enabled LIFE category')

    edited = dict(defaults, name=u'习惯验收', description=u'后台编辑的习惯打卡说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'habit-admin'],
                  sortOrder=631, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"habit_tracker"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'), 'edited habit metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='78'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source); sql(source)
    check([sql(query) for query in queries] == snapshot, 'V78 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'seed replay preserves edited habit', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'), 'restored habit metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'habit increments daily and all-time usage')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE) == '2',
          'habit daily opens use the Beijing date')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'habit participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes habit usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'habit participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_habit_tracker_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
