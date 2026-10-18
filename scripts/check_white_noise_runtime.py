# -*- coding: utf-8 -*-
"""V83 white_noise static and isolated backend acceptance."""
from __future__ import print_function
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'white_noise'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=white_noise&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V83__seed_white_noise_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/noise')
RESOURCE_NAME = re.compile(r'^noise_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [u'白噪音', u'粉噪音', u'褐噪音', u'雨声', u'海浪', u'助眠',
               'white noise', 'soundscape', 'sleep']
KEYWORDS_EN = ['white noise', 'pink noise', 'brown noise', 'rain', 'ocean', 'sleep',
               'ambience', 'soundscape', u'白噪音']
CONTRACT = {
    'code': CODE,
    'name': u'白噪音',
    'description': u'在本机合成白、粉、褐噪音和雨声、海浪、风扇等环境声，可混音、调节音量并设置睡眠定时。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 635,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'White noise',
    'description': ('Synthesize white, pink and brown noise plus rain, ocean and fan ambience locally. '
                    'Mix layers, adjust volumes and set a sleep timer.'),
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('six soundscape layers', 'enum class NoiseType'),
    ('scene presets', 'enum class NoisePreset'),
    ('timer options', 'val timerOptions = listOf(0, 15, 30, 60, 90, 180)'),
    ('normalized layer volume', 'fun adjusted(settings: NoiseSettings, type: NoiseType, level: Float)'),
    ('safe layer toggle', 'fun toggled(settings: NoiseSettings, type: NoiseType)'),
    ('mix weighting', 'fun mixWeight(levels: Map<NoiseType, Float>): Double'),
    ('monotonic timer milliseconds', 'fun timerMillis(minutes: Int): Long?'),
    ('monotonic timer deadline', 'fun deadline(remainingMillis: Long?, nowMillis: Long): Long?'),
    ('sleep fade boundary', 'const val FADE_OUT_MILLIS = 15_000L'),
    ('continuous layer state', 'class NoiseLayerState(seed: Long)'),
    ('stateful pink filter', 'pink0 = 0.99886 * pink0 + white * 0.0555179'),
    ('stateful brown filter', 'brown = (brown + white * 0.042) / 1.018'),
    ('rain drop envelope', 'dropEnvelope *= 0.99965'),
    ('ocean swell', 'val swell = 0.28 + 0.72'),
    ('fan low hum', 'fanPhase += 2.0 * PI * 58.0 / sampleRate'),
    ('procedural mix renderer', 'fun renderMixed(settings: NoiseSettings, states: MutableMap<NoiseType, NoiseLayerState>): FloatArray'),
    ('soft limiting', 'kotlin.math.tanh(normalized * 1.1f)'),
)
IMPLEMENTATION_FEATURES = (
    ('44.1 kHz output', 'const val SAMPLE_RATE = 44_100'),
    ('1024-frame chunks', 'const val RENDER_CHUNK_FRAMES = 1024'),
    ('media AudioAttributes', 'setUsage(AudioAttributes.USAGE_MEDIA)'),
    ('streaming AudioTrack', 'setTransferMode(AudioTrack.MODE_STREAM)'),
    ('single audio thread', 'Thread(::runLoop, "noise-audio")'),
    ('audio thread priority', 'Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)'),
    ('blocking PCM writes', 'AudioTrack.WRITE_BLOCKING'),
    ('stereo PCM', 'AudioFormat.CHANNEL_OUT_STEREO'),
    ('startup gain ramp', '1f / (NoiseSynthesis.SAMPLE_RATE * 0.18f)'),
    ('sleep fade smoothing', 'smoothedFade += (fadeTarget - smoothedFade).coerceIn(-fadeStep, fadeStep)'),
    ('track release', 'created.release()'),
    ('audio failure reporting', 'private val failureReported = AtomicBoolean(false)'),
    ('reversible engine restart', 'return attempt'),
    ('attempt-tagged failure', 'onFailure?.invoke(attempt)'),
    ('failed engine refused reuse', 'if (failureReported.get()) return null'),
    ('audio track close wait', 'thread?.join(500L)'),
    ('serialized audio shutdown', 'val canExit = synchronized(this)'),
    ('settings persistence', 'getSharedPreferences(PREFERENCES'),
    ('service reload', 'NoisePlaybackService.reload(appContext)'),
)
SERVICE_FEATURES = (
    ('media foreground service type', 'android:foregroundServiceType="mediaPlayback"'),
    ('foreground media permission', 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK'),
    ('foreground start helper', 'context.startForegroundService(intent)'),
    ('foreground before focus', 'startForeground(NOTIFICATION_ID, buildNotification(settings))\n        if (!requestAudioFocus())'),
    ('monotonic clock', 'SystemClock.elapsedRealtime()'),
    ('audio focus request', 'AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)'),
    ('focus rejection cleanup', 'if (!requestAudioFocus())'),
    ('headphone disconnect receiver', 'AudioManager.ACTION_AUDIO_BECOMING_NOISY'),
    ('exclusive broadcast registration', 'ContextCompat.RECEIVER_NOT_EXPORTED'),
    ('generation token', 'sessionGeneration++'),
    ('ticker generation token', 'tickerGeneration == generation'),
    ('sleep fade', 'NoisePlaybackPolicy.fadeMultiplier(remainingMillis)'),
    ('notification transport category', 'Notification.CATEGORY_TRANSPORT'),
    ('notification toggle action', 'if (playing) ACTION_PAUSE else ACTION_RESUME'),
    ('notification stop action', 'ACTION_STOP, STOP_REQUEST'),
    ('restart intent', 'KEY_SHOULD_PLAY'),
    ('persisted service state', 'private const val KEY_STATE = "state"'),
    ('persisted remaining timer', 'private const val KEY_REMAINING = "remaining_millis"'),
    ('persisted timer deadline', 'private const val KEY_DEADLINE = "deadline_elapsed_millis"'),
    ('durable service state write', 'servicePreferences().edit(commit = true)'),
    ('paused process restore', 'STATE_PAUSED ->'),
    ('timer fade reset', 'engine?.setFadeMultiplier(NoisePlaybackPolicy.fadeMultiplier(remainingMillis))'),
    ('paused notification refresh', 'if (playing || paused) {\n            startForeground(NOTIFICATION_ID, buildNotification(store.read()))'),
    ('last layer stops playback', 'if (settings.activeTypes.isEmpty()) {\n            if (playing || paused) stopPlayback()'),
    ('focus rejection feedback', 'publish(playing = false, paused = false, error = NoiseError.AUDIO_UNAVAILABLE)'),
    ('engine failure cleanup', 'stopPlayback(discardEngine = true)'),
    ('attempt-scoped engine failure', 'engineAttemptId == attempt && playing'),
    ('current-start service stop', 'stopSelfResult(startId)'),
    ('destroy preserves error', 'NoisePlaybackRuntime.publish(NoisePlaybackState(error = error))'),
    ('focus release on pause', 'pausePlayback()'),
    ('service cleanup', 'unregisterReceiver(noisyReceiver)'),
)
UI_FEATURES = (
    ('notification permission card', 'NoisePermissionCard {'),
    ('notification permission launcher', 'ActivityResultContracts.RequestPermission()'),
    ('scene chips', 'NoisePreset.entries.filter'),
    ('scene action semantics', 'AssistChip('),
    ('timer chips', 'NoisePolicy.timerOptions.forEach'),
    ('six-layer switches', 'ToolboxSwitch('),
    ('named layer switch semantics', 'contentDescription = label'),
    ('master and layer volume', 'VolumeControl('),
    ('named layer volume', 'R.string.noise_layer_volume_for'),
    ('play pause resume stop', 'R.string.noise_resume'),
    ('status badge', 'ToolboxBadge(status'),
    ('remaining timer', 'R.string.noise_timer_remaining'),
    ('responsive rows', 'FlowRow('),
    ('scroll state', 'rememberLazyListState()'),
    ('IME safe content', 'imePadding()'),
    ('styled control button', 'ToolboxButton('),
    ('styled outlined button', 'ToolboxOutlinedButton('),
)


def check_seed(check):
    source = read_source(SEED_PATH)
    check(source is not None, 'V83 catalog seed exists')
    migration_dir = os.path.dirname(SEED_PATH)
    files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V83__'))
    check(files == [os.path.basename(SEED_PATH)], 'V83 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured',
    ], 'V83 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'], 'V83 metadata and ordered values match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V83 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*635\s*,\s*0\s*\)", body)
          is not None, 'V83 defaults and sort match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V83 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V83 replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_resources(check):
    feature = ''
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                feature += '\n' + read_source(os.path.join(current, name))
    string_refs = set(re.findall(r'R\.string\.(noise_[A-Za-z0-9_]+)', feature))
    plural_refs = set(re.findall(r'R\.plurals\.(noise_[A-Za-z0-9_]+)', feature))
    arrays = set(re.findall(r'R\.array\.(noise_[A-Za-z0-9_]+)', feature))
    check({'noise_name', 'noise_description', 'noise_play', 'noise_pause', 'noise_stop'}.issubset(string_refs),
          'Noise metadata and primary controls are referenced')
    check(plural_refs == {'noise_minutes'}, 'Noise minute plurals are referenced')
    check(arrays == {'noise_keywords'}, 'Noise keyword array is referenced')
    translations = {}
    plurals = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH),
                                        ('values-en', ENGLISH, KEYWORDS_EN)):
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
              'Android ' + locale + ' noise strings are unique and nonempty')
        check(set(translations[locale]) == string_refs,
              'Android ' + locale + ' noise string references are exact')
        check(set(plurals[locale]) == plural_refs,
              'Android ' + locale + ' noise plural references are exact')
        check(translations[locale].get('noise_name') == expected['name']
              and translations[locale].get('noise_description') == expected['description'],
              'Android ' + locale + ' noise metadata matches')
        keyword_nodes = resources.findall("string-array[@name='noise_keywords']")
        actual = [(node.text or '').strip() for node in keyword_nodes[0].findall('item')] if keyword_nodes else []
        check(len(keyword_nodes) == 1 and actual == keywords,
              'Android ' + locale + ' noise keywords match')
        for name, quantities in plurals[locale].items():
            check(set(quantities) == {'one', 'other'} and all(quantities.values()),
                  'Android ' + locale + ' ' + name + ' has both plural quantities')
    check(set(translations['values']) == set(translations['values-en']), 'Noise bilingual string keys match')
    check(set(plurals['values']) == set(plurals['values-en']), 'Noise bilingual plural keys match')
    check(all(placeholders(value) == placeholders(translations['values-en'][name])
              for name, value in translations['values'].items()), 'Noise bilingual placeholders match')
    check(all(placeholders(quantity) == placeholders(plurals['values-en'][name][kind])
              for name, values in plurals['values'].items()
              for kind, quantity in values.items()), 'Noise bilingual plural placeholders match')


def check_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'NoiseToolDefinition.kt')
    definition = read_source(definition_path)
    for label, token in (
        ('code', 'code = "white_noise"'),
        ('name', 'R.string.noise_name'),
        ('description', 'R.string.noise_description'),
        ('category', 'ToolCategory.LIFE'),
        ('mode', 'ToolMode.LOCAL'),
        ('icon', 'icon = "white_noise"'),
        ('keywords', 'R.array.noise_keywords'),
        ('sort', 'sortOrder = 635'),
        ('featured', 'isFeatured = false'),
    ):
        check(token in definition, 'Noise ToolDefinition ' + label)
    check('requiredPermissions: List<String> = emptyList()' in definition,
          'Noise opening requests no runtime permission')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] *
                              len(re.findall(r'\bcode\s*=\s*"white_noise"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)], 'Noise has exactly one ToolDefinition owner')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and len(re.findall(r'\bNoiseToolDefinition\s*\(', registry.group(0))) == 1,
          'Noise is registered exactly once')
    icons = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(re.search(r'"white_noise"\s*->\s*R\.drawable\.ic_noise\b', icons) is not None,
          'Noise has one icon mapping')
    android = '{http://schemas.android.com/apk/res/android}'
    for path in ('drawable/ic_noise.xml', 'drawable/ic_noise_notification.xml'):
        icon = ET.parse(os.path.join(RES_ROOT, path)).getroot()
        check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
              and icon.get(android + 'height') == '24dp'
              and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
              path + ' is a nonempty 24dp vector')


def check_implementation(check):
    domain = read_source(os.path.join(FEATURE_ROOT, 'domain/NoiseModels.kt'))
    synthesis = read_source(os.path.join(FEATURE_ROOT, 'domain/NoiseSynthesis.kt'))
    for label, token in DOMAIN_FEATURES:
        check(token in domain or token in synthesis, 'Noise domain provides ' + label)
    implementation = ''
    for name in ('domain/NoiseSynthesis.kt', 'data/NoiseAudioEngine.kt', 'data/NoiseSettingsStore.kt',
                 'data/LocalNoiseRepository.kt'):
        implementation += '\n' + read_source(os.path.join(FEATURE_ROOT, name))
    for label, token in IMPLEMENTATION_FEATURES:
        check(token in implementation, 'Noise implementation provides ' + label)
    service = read_source(os.path.join(FEATURE_ROOT, 'service/NoisePlaybackService.kt'))
    manifest = read_source(os.path.join(ROOT, 'android/app/src/main/AndroidManifest.xml'))
    for label, token in SERVICE_FEATURES:
        check(token in service or token in manifest, 'Noise media service provides ' + label)
    check(service.index('startForeground(NOTIFICATION_ID, buildNotification(settings))')
          < service.index('if (!requestAudioFocus())'), 'Noise starts foreground before audio focus')
    check('abandonAudioFocus()' in service[service.index('private fun pausePlayback'):service.index('private fun resumePlayback')],
          'Noise releases audio focus while paused')
    check(service.index('ticker?.cancel()') < service.index('tickerGeneration = generation'),
          'Noise replaces an old timer before starting a new generation')
    for forbidden in ('android.permission.RECORD_AUDIO', 'android.permission.READ_MEDIA_AUDIO',
                      'android.permission.SCHEDULE_EXACT_ALARM', 'android.permission.WAKE_LOCK'):
        check(forbidden not in manifest, 'Noise manifest omits ' + forbidden)
    screen = read_source(os.path.join(FEATURE_ROOT, 'ui/NoiseScreen.kt'))
    for label, token in UI_FEATURES:
        check(token in screen, 'Noise UI provides ' + label)
    for token in ('MaterialTheme.colorScheme', 'MaterialTheme.shapes', 'ToolboxDimens.pageGutter',
                  'ToolboxDimens.formMaxWidth', 'contentDescription = label'):
        check(token in screen, 'Noise UI uses shared token ' + token)
    check('TimeUnit' not in screen and 'Thread.sleep' not in screen, 'Noise UI never blocks the main thread')
    release_gate = read_source(os.path.join(ROOT, 'scripts/check_release_artifacts.py'))
    check('android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK' in release_gate,
          'Release permission gate includes media playback foreground service')
    tests = []
    for current, unused, names in os.walk(os.path.join(ROOT, 'android/app/src/test/java/com/cangshuo/toolbox/feature/noise')):
        tests.extend(name for name in names if name.endswith('Test.kt'))
    check(sorted(tests) == ['NoisePolicyTest.kt', 'NoiseViewModelTest.kt'],
          'Noise has focused policy and ViewModel suites')
    docs = read_source(os.path.join(ROOT, 'docs/WHITE_NOISE.md'))
    check(docs is not None and 'chmouel/noise-timer' in docs and 'surendranb/chromatone' in docs
          and 'eff4e724fba11ca46bd681873da79746b8fe027c' in docs
          and '275f5045cbb648914fde0c74657fe4be674df08d' in docs,
          'Noise documents fixed open-source reviews')


def check_candidate_isolation(check):
    script = read_source(os.path.join(ROOT, 'scripts/check_white_noise_runtime.py'))
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'Noise candidate opts into credential-stripped isolation')


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
    print('White noise offline static acceptance:', checks[0], '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='83' AND success=1") == '1', 'V83 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V83 creates one white-noise row')
    check(CODE in records(), 'public LIFE catalog includes white_noise')
    check_tool_metadata(check, records()[CODE], 'public white noise', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin white-noise detail', defaults)
    check_tool_metadata(check, listing, 'admin white-noise list', defaults)
    check(detail == listing, 'admin white-noise detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers enabled LIFE category')

    edited = dict(defaults, name=u'白噪音验收', description=u'后台编辑的白噪音说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'white-noise-admin'],
                  sortOrder=636, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"white_noise"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited white-noise metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='83'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot, 'V83 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'seed replay preserves edited white noise', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored white-noise metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'White noise increments daily and all-time usage')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'White noise participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes white-noise usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'White noise participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_white_noise_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
