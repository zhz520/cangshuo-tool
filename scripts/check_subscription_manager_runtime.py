# -*- coding: utf-8 -*-
"""V82 subscription_manager static and isolated backend acceptance."""
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
CODE = 'subscription_manager'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=subscription_manager&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V82__seed_subscription_manager_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/subscription')
RESOURCE_NAME = re.compile(r'^subscription_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [u'订阅', u'会员', u'续费', u'账单', u'到期', u'自动续费', 'subscription', 'membership']
KEYWORDS_EN = ['subscription', 'membership', 'renewal', 'billing', 'expiry', 'recurring', u'订阅', u'会员']
CONTRACT = {
    'code': CODE,
    'name': u'订阅管理',
    'description': u'在本机记录会员与订阅服务、周期金额和下次续费日期，查看月均支出并接收续费提醒。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 634,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Subscriptions',
    'description': 'Track memberships and recurring services locally, review monthly spend and get renewal reminders.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('subscription categories', 'enum class SubscriptionCategory'),
    ('billing cycles', 'enum class BillingCycle'),
    ('filters', 'enum class SubscriptionFilter'),
    ('sorts', 'enum class SubscriptionSort'),
    ('due states', 'enum class DueState'),
    ('item limit', 'const val MAX_ITEMS = 500'),
    ('price ceiling', 'const val MAX_PRICE_CENTS = 99_999_999L'),
    ('name budget', 'const val MAX_NAME_CODE_POINTS = 80'),
    ('reminder bounds', 'const val MAX_REMINDER_DAYS = 30'),
    ('custom cycle bounds', 'const val MAX_CUSTOM_DAYS = 3_650'),
    ('fixed-point price parser', 'fun parsePriceCents(text: String): Long?'),
    ('yearly conversion', 'fun yearlyCostCents(priceCents: Long, cycle: BillingCycle, customDays: Int): Long'),
    ('monthly conversion', 'fun monthlyCostCents(priceCents: Long, cycle: BillingCycle, customDays: Int): Long'),
    ('anchor-based cycle advance', 'base.plusMonths(3L * cycles)'),
    ('future payment calculation', 'fun nextFuturePayment(item: SubscriptionItem, todayEpochDay: Long): Long'),
    ('automatic renewal display', 'fun effectiveNextPayment(item: SubscriptionItem, todayEpochDay: Long): Long'),
    ('due state', 'fun dueState(item: SubscriptionItem, todayEpochDay: Long): DueState'),
    ('search/filter/sort', 'fun filterAndSort('),
    ('statistics', 'fun stats(items: List<SubscriptionItem>, todayEpochDay: Long): SubscriptionStats'),
    ('reminder schedule synchronization', 'fun syncReminderSchedule(items: List<SubscriptionItem>)'),
)
REPOSITORY_FEATURES = (
    ('Room mapping', 'dao.observeAll().map'),
    ('bounded transactional insert', 'dao.insertIfBelowLimit(item.toEntity(), SubscriptionPolicy.MAX_ITEMS)'),
    ('missing update check', 'check(dao.update(item.toEntity()) > 0)'),
    ('device local day', 'LocalDate.now().toEpochDay()'),
)
VIEWMODEL_FEATURES = (
    ('saved filter', 'saved.get<String>("subscriptionFilter")'),
    ('saved sort', 'saved.get<String>("subscriptionSort")'),
    ('saved query', 'saved.get<String>("subscriptionQuery")'),
    ('duplicate save guard', 'if (mutable.value.saving) return false'),
    ('early item limit guard', 'allItems.size >= SubscriptionPolicy.MAX_ITEMS'),
    ('busy item guard', 'if (id in mutable.value.busyItemIds) return'),
    ('reminder schedule sync', 'syncReminderSchedule()'),
    ('message revision', 'messageRevision = mutable.value.messageRevision + 1'),
)
UI_FEATURES = (
    ('overview stats', 'SubscriptionStatsCard('),
    ('notification permission card', 'NotificationPermissionCard {'),
    ('search field', 'R.string.subscription_search'),
    ('filter chips', 'SubscriptionFilter.entries.forEach'),
    ('sort chips', 'SubscriptionSort.entries.forEach'),
    ('loading state', 'ToolboxLoadingState('),
    ('empty state', 'SubscriptionEmptyState('),
    ('subscription card', 'SubscriptionCard('),
    ('record renewal', 'R.string.subscription_renew'),
    ('pause and resume', 'R.string.subscription_resume'),
    ('details dialog', 'SubscriptionDetailDialog('),
    ('editor dialog', 'SubscriptionEditorDialog('),
    ('custom cycle input', 'R.string.subscription_custom_days_label'),
    ('date picker', 'DatePickerDialog('),
    ('reminder switch', 'R.string.subscription_reminder_enabled_label'),
    ('auto-renew switch', 'R.string.subscription_auto_renew_label'),
    ('delete confirmation', 'R.string.subscription_delete_title'),
    ('item-specific accessibility', 'R.string.subscription_action_for_item'),
    ('responsive wrapping', 'FlowRow('),
    ('snackbar host', 'SnackbarHost('),
)


def check_seed(check):
    source = read_source(SEED_PATH)
    check(source is not None, 'V82 catalog seed exists')
    migration_dir = os.path.dirname(SEED_PATH)
    files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V82__'))
    check(files == [os.path.basename(SEED_PATH)], 'V82 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured',
    ], 'V82 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'], 'V82 metadata and ordered values match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V82 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*634\s*,\s*0\s*\)", body)
          is not None, 'V82 defaults and sort match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V82 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V82 replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_resources(check):
    feature = ''
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                feature += '\n' + read_source(os.path.join(current, name))
    string_refs = set(re.findall(r'R\.string\.(subscription_[A-Za-z0-9_]+)', feature))
    plural_refs = set(re.findall(r'R\.plurals\.(subscription_[A-Za-z0-9_]+)', feature))
    arrays = set(re.findall(r'R\.array\.(subscription_[A-Za-z0-9_]+)', feature))
    check({'subscription_name', 'subscription_description', 'subscription_search', 'subscription_save'}.issubset(string_refs),
          'Subscription metadata and primary controls are referenced')
    check(plural_refs == {'subscription_cycle_custom_days', 'subscription_reminder_days_value', 'subscription_notification_message'},
          'Subscription plurals are referenced')
    check(arrays == {'subscription_keywords'}, 'Subscription keyword array is referenced')
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
              'Android ' + locale + ' Subscription strings are unique and nonempty')
        check(set(translations[locale]) == string_refs,
              'Android ' + locale + ' Subscription string references are exact')
        check(set(plurals[locale]) == plural_refs,
              'Android ' + locale + ' Subscription plural references are exact')
        check(translations[locale].get('subscription_name') == expected['name']
              and translations[locale].get('subscription_description') == expected['description'],
              'Android ' + locale + ' Subscription metadata matches')
        keyword_nodes = resources.findall("string-array[@name='subscription_keywords']")
        actual = [(node.text or '').strip() for node in keyword_nodes[0].findall('item')] if keyword_nodes else []
        check(len(keyword_nodes) == 1 and actual == keywords,
              'Android ' + locale + ' Subscription keywords match')
        for name, quantities in plurals[locale].items():
            check(set(quantities) == {'one', 'other'} and all(quantities.values()),
                  'Android ' + locale + ' ' + name + ' has both plural quantities')
    check(set(translations['values']) == set(translations['values-en']), 'Subscription bilingual string keys match')
    check(set(plurals['values']) == set(plurals['values-en']), 'Subscription bilingual plural keys match')
    check(all(placeholders(value) == placeholders(translations['values-en'][name])
              for name, value in translations['values'].items()), 'Subscription bilingual placesholders match')
    check(all(placeholders(quantity) == placeholders(plurals['values-en'][name][kind])
              for name, values in plurals['values'].items()
              for kind, quantity in values.items()), 'Subscription bilingual plural placeholders match')


def check_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'SubscriptionToolDefinition.kt')
    definition = read_source(definition_path)
    for label, token in (
        ('code', 'code = "subscription_manager"'),
        ('name', 'R.string.subscription_name'),
        ('description', 'R.string.subscription_description'),
        ('category', 'ToolCategory.LIFE'),
        ('mode', 'ToolMode.LOCAL'),
        ('icon', 'icon = "subscription_manager"'),
        ('keywords', 'R.array.subscription_keywords'),
        ('sort', 'sortOrder = 634'),
        ('featured', 'isFeatured = false'),
    ):
        check(token in definition, 'Subscription ToolDefinition ' + label)
    check('requiredPermissions: List<String> = emptyList()' in definition,
          'Subscription requests no opening permission')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] *
                              len(re.findall(r'\bcode\s*=\s*"subscription_manager"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)], 'Subscription has exactly one ToolDefinition owner')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and len(re.findall(r'\bSubscriptionToolDefinition\s*\(', registry.group(0))) == 1,
          'Subscription is registered exactly once')
    icons = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(re.search(r'"subscription_manager"\s*->\s*R\.drawable\.ic_subscription\b', icons) is not None,
          'Subscription has one icon mapping')
    android = '{http://schemas.android.com/apk/res/android}'
    for path in ('drawable/ic_subscription.xml', 'drawable/ic_subscription_notification.xml'):
        icon = ET.parse(os.path.join(RES_ROOT, path)).getroot()
        check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
              and icon.get(android + 'height') == '24dp'
              and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
              path + ' is a nonempty 24dp vector')


def check_database(check):
    database = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabase.kt'))
    check('SubscriptionItemEntity::class' in database and 'version = 13' in database
          and 'abstract fun subscriptionItemDao(): SubscriptionItemDao' in database,
          'Room exports subscriptions at schema version 13')
    migration = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabaseMigrations.kt'))
    check('val MIGRATION_12_13 = object : Migration(12, 13)' in migration,
          'Room declares 12 to 13 migration')
    for token in ('CREATE TABLE IF NOT EXISTS subscription_item', 'name TEXT NOT NULL',
                  'provider TEXT NOT NULL', 'price_cents INTEGER NOT NULL',
                  'billing_cycle INTEGER NOT NULL', 'custom_cycle_days INTEGER NOT NULL',
                  'billing_anchor_epoch_day INTEGER NOT NULL',
                  'next_payment_epoch_day INTEGER NOT NULL', 'reminder_enabled INTEGER NOT NULL',
                  'reminder_days INTEGER NOT NULL', 'auto_renew INTEGER NOT NULL',
                  'active INTEGER NOT NULL', 'index_subscription_item_active',
                  'index_subscription_item_next_payment_epoch_day', 'index_subscription_item_updated_at'):
        check(token in migration, 'Room migration contains ' + token)
    schema_path = os.path.join(ROOT, 'android/app/schemas/com.cangshuo.toolbox.core.database.ToolboxDatabase/13.json')
    schema = json.loads(read_source(schema_path))
    check(schema['database']['version'] == 13, 'Room schema 13 exported')
    entities = dict((row['tableName'], row) for row in schema['database']['entities'])
    check('subscription_item' in entities, 'Room schema contains subscription_item')
    table = entities['subscription_item']
    check(table['primaryKey'] == {'autoGenerate': True, 'columnNames': ['id']},
          'Subscription uses a generated id')
    check(set(row['columnName'] for row in table['fields']) == {
        'id', 'name', 'provider', 'category', 'price_cents', 'billing_cycle',
        'custom_cycle_days', 'start_epoch_day', 'billing_anchor_epoch_day', 'next_payment_epoch_day',
        'payment_method', 'notes',
        'reminder_enabled', 'reminder_days', 'auto_renew', 'active', 'created_at', 'updated_at',
    }, 'Subscription columns match migration')
    check(set(tuple(index['columnNames']) for index in table.get('indices', [])) == {
        ('active',), ('next_payment_epoch_day',), ('updated_at',),
    }, 'Subscription exports all three indexes')
    provider = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabaseProvider.kt'))
    check('MIGRATION_12_13' in provider and 'instance ?: synchronized(this)' in provider,
          'App-scoped database provider includes V13 migration')
    dao = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/SubscriptionItemDao.kt'))
    for label, token in (
        ('transactional count', '@Transaction'),
        ('bounded count query', 'SELECT COUNT(*) FROM subscription_item'),
        ('transactional bounded insert', 'suspend fun insertIfBelowLimit(item: SubscriptionItemEntity, limit: Int): Long'),
        ('limit sentinel', 'if (countItems() >= limit) return -1L'),
        ('checked update', 'suspend fun update(item: SubscriptionItemEntity): Int'),
        ('background history query', 'suspend fun getAll(): List<SubscriptionItemEntity>'),
    ):
        check(token in dao, 'Subscription DAO provides ' + label)


def check_implementation(check):
    domain = read_source(os.path.join(FEATURE_ROOT, 'domain/SubscriptionModels.kt'))
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'Subscription domain provides ' + label)
    check('BigDecimal("365.2425")' in domain and 'RoundingMode.HALF_UP' in domain,
          'Subscription conversions use deterministic decimal rounding')
    repository = read_source(os.path.join(FEATURE_ROOT, 'data/RoomSubscriptionRepository.kt'))
    for label, token in REPOSITORY_FEATURES:
        check(token in repository, 'Subscription repository provides ' + label)
    view_model = read_source(os.path.join(FEATURE_ROOT, 'ui/SubscriptionViewModel.kt'))
    for label, token in VIEWMODEL_FEATURES:
        check(token in view_model, 'Subscription ViewModel provides ' + label)
    screen = read_source(os.path.join(FEATURE_ROOT, 'ui/SubscriptionScreen.kt'))
    for label, token in UI_FEATURES:
        check(token in screen, 'Subscription UI provides ' + label)
    for token in ('safeDrawingPadding()', 'imePadding()', 'verticalScroll(rememberScrollState())',
                  'rememberLazyListState()', 'widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth()',
                  'messageRevision', 'ActivityResultContracts.RequestPermission()'):
        check(token in screen, 'Subscription UI contains ' + token)
    scheduler = read_source(os.path.join(FEATURE_ROOT, 'reminder/SubscriptionReminderScheduler.kt'))
    for token in ('AlarmManager.RTC_WAKEUP', 'setAndAllowWhileIdle', 'LocalTime.of(9, 0)',
                  'Intent.ACTION_BOOT_COMPLETED', 'ACTION_CHECK'):
        check(token in scheduler or token in read_source(os.path.join(FEATURE_ROOT, 'reminder/SubscriptionBootReceiver.kt')),
              'Subscription reminder contains ' + token)
    receiver = read_source(os.path.join(FEATURE_ROOT, 'reminder/SubscriptionReminderReceiver.kt'))
    for token in ('goAsync()', 'POST_NOTIFICATIONS', 'NotificationChannel(',
                  'Notification.CATEGORY_REMINDER', 'getQuantityString(', 'pending.finish()'):
        check(token in receiver, 'Subscription notification receiver contains ' + token)
    manifest = read_source(os.path.join(ROOT, 'android/app/src/main/AndroidManifest.xml'))
    check('android.permission.RECEIVE_BOOT_COMPLETED' in manifest
          and '.feature.subscription.reminder.SubscriptionReminderReceiver' in manifest
          and '.feature.subscription.reminder.SubscriptionBootReceiver' in manifest,
          'Subscription alarm and boot receivers are declared')
    check('SCHEDULE_EXACT_ALARM' not in manifest and 'USE_EXACT_ALARM' not in manifest,
          'Subscription reminders require no exact-alarm permission')
    tests = []
    for current, unused, names in os.walk(os.path.join(ROOT, 'android/app/src/test/java/com/cangshuo/toolbox/feature/subscription')):
        tests.extend(name for name in names if name.endswith('Test.kt'))
    check(sorted(tests) == ['SubscriptionPolicyTest.kt', 'SubscriptionViewModelTest.kt'],
          'Subscription has focused policy and ViewModel suites')
    docs = read_source(os.path.join(ROOT, 'docs/SUBSCRIPTION_MANAGER.md'))
    check(docs is not None and 'Grigoriym/Wallosmobile' in docs and 'Ike-li/subscription-charge-guardian' in docs
          and '9b8978fb2c5986d32a69df8a961a5e8862f969fc' in docs
          and '082ef5de06133dda7c229fb02eecc68b02b411c9' in docs,
          'Subscription documents fixed open-source reviews')


def check_candidate_isolation(check):
    script = read_source(os.path.join(ROOT, 'scripts/check_subscription_manager_runtime.py'))
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'Subscription candidate opts into credential-stripped isolation')
    release_gate = read_source(os.path.join(ROOT, 'scripts/check_release_artifacts.py'))
    check('android.permission.RECEIVE_BOOT_COMPLETED' in release_gate,
          'Release permission gate includes boot-completed permission')


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
    print('Subscription manager offline static acceptance:', checks[0], '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='82' AND success=1") == '1', 'V82 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V82 creates one subscription row')
    check(CODE in records(), 'public LIFE catalog includes subscription_manager')
    check_tool_metadata(check, records()[CODE], 'public subscription manager', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin subscription detail', defaults)
    check_tool_metadata(check, listing, 'admin subscription list', defaults)
    check(detail == listing, 'admin subscription detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers enabled LIFE category')

    edited = dict(defaults, name=u'订阅管理验收', description=u'后台编辑的订阅管理说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'subscription-admin'],
                  sortOrder=635, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"subscription_manager"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited subscription metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='82'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot, 'V82 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'seed replay preserves edited subscription', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored subscription metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'Subscription increments daily and all-time usage')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'Subscription participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes subscription usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'Subscription participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_subscription_manager_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
