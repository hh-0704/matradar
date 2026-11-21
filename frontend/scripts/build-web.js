#!/usr/bin/env node
/**
 * 웹 프로덕션 빌드. (발췌 — env 검사 · 산출물 검증 · 실행 순서만 싣는다. 오류 안내 문구는 줄였다)
 *
 * `expo export -p web` 을 그대로 쓰지 않는 이유 — 다음이 빠지기 때문이다.
 *
 *  1) env 가드: `EXPO_PUBLIC_*` 는 빌드 타임에 번들로 인라인된다. 값이 없어도 빌드는 성공하고 API 주소 폴백
 *     (`http://localhost:8080`)이 번들에 박혀, 배포는 됐는데 운영에서 API 만 전부 실패하는 상태가 된다.
 *  2) dev 라우트 제외: 라우터가 `__*__/` 폴더를 걸러내지 않아 개발용 화면이 운영 번들에 들어간다.
 *     빌드 동안만 폴더를 작업 트리 밖으로 옮겨 번들 자체에서 빠지게 한다.
 *  3) 산출물 정리·검증: mock 번들 제거, 보안 헤더(CSP) 치환, 그리고 "배포 뒤에야 드러나는 실패" 를 여기서 끊는다.
 *
 * 원칙: **빌드가 성공했다는 것과 산출물이 옳다는 것은 다르다** — 입력(env)만 보지 않고 결과물(번들·헤더)을 연다.
 */

const { execSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..');
const DIST_DIR = path.join(ROOT, 'dist');
const DEV_ROUTE_PATTERN = /^__.+__$/;

function fail(message, hint) {
    console.error(`\n✖ 빌드 중단 — ${message}`);
    if (hint) console.error(`  ${hint}`);
    process.exit(1);
}

function checkEnv() {
    const apiUrl = process.env.EXPO_PUBLIC_API_URL;

    if (!apiUrl) {
        fail(
            'EXPO_PUBLIC_API_URL 이 설정되지 않았다.',
            '없이 빌드하면 번들에 localhost 폴백이 박혀 운영에서 API 가 전부 실패한다.',
        );
    }
    if (!/^https?:\/\//.test(apiUrl)) {
        fail(`EXPO_PUBLIC_API_URL 이 절대 URL 이 아니다: ${apiUrl}`);
    }
    // 산출물을 로컬에서 띄워 보는 건 정상 작업이라 ALLOW_LOCAL_API=1 로 명시할 때만 연다.
    // 기본을 막아 두어야 CI 에서 로컬 주소가 박힌 채 배포되는 일이 없다.
    const isLocalApi = apiUrl.includes('localhost') || apiUrl.includes('127.0.0.1');
    if (isLocalApi && process.env.ALLOW_LOCAL_API !== '1') {
        fail(`EXPO_PUBLIC_API_URL 이 로컬 주소다: ${apiUrl}`, '로컬 확인용이면 ALLOW_LOCAL_API=1 을 함께 지정할 것.');
    }
    // … 로컬 API 빌드 경고 출력 · 지도 SDK 키 확인 생략
    if (process.env.EXPO_PUBLIC_USE_MOCK === 'true') {
        fail('EXPO_PUBLIC_USE_MOCK=true 인 채로 운영 빌드를 하려 한다.', 'mock 응답이 그대로 배포된다.');
    }
    console.log('✓ env 확인');
}

function verifyDist() {
    const leaked = fs
        .readdirSync(DIST_DIR, { withFileTypes: true })
        .filter((e) => e.isDirectory() && DEV_ROUTE_PATTERN.test(e.name))
        .map((e) => e.name);
    if (leaked.length > 0) {
        fail(`dist 에 dev 라우트가 남아 있다: ${leaked.join(', ')}`);
    }
    if (!fs.existsSync(path.join(DIST_DIR, '_redirects'))) {
        fail('dist/_redirects 가 없다.', 'SPA fallback 이 빠지면 /place/40 같은 직접 진입이 404 가 된다.');
    }

    // 보안 헤더 — 없거나 치환이 덜 된 채 배포되면 CSP 가 통째로 무효가 된다(문법 오류면 브라우저가 무시한다).
    const headersPath = path.join(DIST_DIR, '_headers');
    if (!fs.existsSync(headersPath)) {
        fail('dist/_headers 가 없다.', '보안 헤더(CSP 등) 없이 배포된다.');
    }
    const headers = fs.readFileSync(headersPath, 'utf8');
    for (const token of ['__API_ORIGIN__', '__SCRIPT_HASHES__']) {
        if (headers.includes(token)) {
            fail(`dist/_headers 에 치환되지 않은 ${token} 이 남아 있다.`);
        }
    }
    // 산출물의 인라인 스크립트가 전부 CSP 해시로 허용돼 있는지 교차 확인 — 하나라도 빠지면 hydrate 가 막혀 흰 화면이 된다.
    const missing = collectInlineScriptHashes().filter((h) => !headers.includes(h));
    if (missing.length > 0) {
        fail(`dist/_headers 의 CSP 가 인라인 스크립트 ${missing.length}개를 허용하지 않는다.`, '그대로 배포하면 앱이 뜨지 않는다.');
    }
    console.log('✓ 보안 헤더 확인 (치환 완료, 인라인 script 전부 허용)');

    // env 가 실제로 번들에 인라인됐는지 산출물로 확인한다. checkEnv() 는 "값이 주어졌는지" 만 보므로
    // 캐시 때문에 값이 반영되지 않은 경우를 잡지 못한다.
    const bundleDir = path.join(DIST_DIR, '_expo/static/js/web');
    if (fs.existsSync(bundleDir)) {
        const bundles = fs
            .readdirSync(bundleDir)
            .filter((f) => f.endsWith('.js'))
            .map((f) => fs.readFileSync(path.join(bundleDir, f), 'utf8'));

        const apiUrl = process.env.EXPO_PUBLIC_API_URL;
        if (!bundles.some((b) => b.includes(apiUrl))) {
            fail(`번들에 EXPO_PUBLIC_API_URL(${apiUrl}) 이 인라인되지 않았다.`, 'Metro 캐시나 빌드 설정 문제일 수 있다.');
        }
        // 로컬 확인용 빌드(ALLOW_LOCAL_API=1)는 localhost 가 들어 있는 게 정상이라 건너뛴다.
        const intentionalLocal =
            process.env.ALLOW_LOCAL_API === '1' && apiUrl.includes('localhost');
        if (!intentionalLocal && bundles.some((b) => b.includes('http://localhost:8080'))) {
            fail('번들에 localhost 폴백이 살아 있다.', '운영에서 API 가 전부 실패한다.');
        }
        console.log('✓ 번들 env 인라인 확인');
    }

    // … mock 번들 잔존 검사 생략
}

function main() {
    checkEnv();

    let stashed = [];
    try {
        stashed = stashDevRoutes();
        fs.rmSync(DIST_DIR, { recursive: true, force: true });
        // --clear 필수 — Metro 캐시가 남아 있으면 EXPO_PUBLIC_* 변경이 반영되지 않는다.
        // env 를 바꿔도 조용히 옛 값으로 배포되는 사고라, 운영 빌드는 항상 캐시 없이 간다.
        execSync('npx expo export -p web --clear', { stdio: 'inherit', cwd: ROOT });
    } finally {
        // 빌드가 실패해도 작업 트리는 반드시 되돌린다.
        restoreDevRoutes(stashed);
    }

    cleanDist();
    relocateNodeModulesAssets(); // applyHeaders 보다 먼저 — 인라인 스크립트 해시는 최종 HTML 로 계산해야 한다
    applyHeaders();
    verifyDist();
    verifyVendorAssets();
    console.log('\n✓ 빌드 완료 → dist/');
}

// … stashDevRoutes · restoreDevRoutes · cleanDist · relocateNodeModulesAssets · applyHeaders
//   · collectInlineScriptHashes · verifyVendorAssets 생략

main();
