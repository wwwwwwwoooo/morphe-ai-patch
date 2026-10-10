# LyricBridge: ChatGPT 계정 연결 및 AI 동기화 가사 번역

이 기능은 **새 선택형 패치 `AI synced lyrics translation`** 로 제공된다.
`Third-party lyrics`에 의존하며 기존 OpenAI 호환 번역, Google 번역, 제공자 번역,
로마자 표기, 제공자 새로고침 및 PR #3372의 가사 검색/프롬프트 기능을 보존한다.
사용자 지정 배포 번들은 원본과 구별하도록 `LyricBridge Patches` 명칭을 사용하고
원본 LICENSE/NOTICE를 포함한다. 새 기능 기본값은 OFF다. 켜면 번역 버튼이 새 엔진을 사용하고, 끄면 기존 번역
경로를 사용한다. 새 엔진은 실패 시 다른 제공자나 결제 방식으로 자동 전환하지 않는다.

## 기반 저장소와 기존 구조

- 기반: `MorpheApp/morphe-patches`, v1.46.0, commit `1bcd0bced`.
- PR #3372: 병합 완료, merge `f2b32016108b112458aaf09384423faa37910983`.
  가사 제공자 검색과 OpenAI 호환 프롬프트 편집을 이미 제공한다.
- 패치: `patches/.../music/layout/lyrics/LyricsPatch.kt`.
- 제공자/정규화: `LyricsManager` 및 `lyrics/requests`의 기존 제공자 구현 재사용.
  LRCLIB, Musixmatch, Spotify, YTMusic, NetEase, Apple Music, QQ, KuGou,
  PetitLyrics, 로컬 파일 등을 지원한다. 제공자별 가용성 및 사용 조건은 그대로 적용된다.
- 가사 데이터: `Lyrics`, `LyricsLine`의 원래 시간, 배경 보컬 및 듀엣 정보를 유지한다.
- 화면: `LyricsPanelView`의 기존 강조, 스크롤 및 보조 번역 영역 재사용.
- 기존 캐시: `LyricsCache` 그대로 유지. 새 기능은 별도 전용 캐시를 사용한다.
- 인증: 별도 Activity나 웹뷰 없이 시스템 브라우저와 IPv4 loopback listener 사용.
  원래 YT Music의 INTERNET 권한을 사용하며 추가 공개 서버가 없다.

## 주요 변경 파일

| 위치 | 역할 |
|---|---|
| `patches/.../lyrics/AiLyricsTranslationPatch.kt` | 기본 선택 OFF인 독립 패치와 새 설정 그룹 |
| `extensions/music/.../lyrics/ai/OAuthProtocol.java`, `OAuthTokenClient.java`, `LoopbackReceiver.java`, `IdTokenValidator.java` | 공식 public-client 등록/PKCE/콜백/ID 토큰 검증 |
| `lyrics/ai/ChatGptSession.java`, `SecureSessionStore.java`, `SessionTokens.java` | 계정별 세션, 갱신·해제와 Keystore 저장 |
| `lyrics/ai/ResponsesClient.java`, `ApiTransport.java`, `RequestCancellation.java` | 모델 카탈로그, SSE, 오류·타임아웃·취소 |
| `lyrics/ai/TranslationFormat.java`, `TranslationDiskCache.java`, `LyricsTranslationEngine.java` | 전체 문맥 번역, 줄 ID 검증, 중복 방지와 전용 캐시 |
| `settings/Settings.java`, `settings/preference/ChatGptAccountPreference.java`, `ChatGptModelPreference.java`, `AiTranslationCachePreference.java` | 새 설정과 계정/모델/캐시 UI |
| `lyrics/LyricsTranslator.java`, `LyricsManager.java`, `ui/LyricsPanelView.java` | 새 엔진 선택, 곡 변경 취소, 기존 렌더러 및 새 AI 메뉴 연결 |
| 기본/한국어 `music/strings.xml`, `arrays.xml` | 새 설정·상태·오류 메시지와 번역 스타일 |
| `src/test/.../AiProtocolTest.java`, `tools/ai-lyrics-tests` | SDK 없이 재현 가능한 18개 프로토콜 테스트 |
| `src/androidTest/.../AndroidSessionTest.java`, `src/debug/AndroidManifest.xml` | 실제 Android용 Keystore/loopback 계측 테스트와 테스트 앱 권한 |
| `patches/build.gradle.kts` | 사용자 지정 번들 이름 옵션 및 LICENSE/NOTICE 포함 |

`extensions/music/...`의 전체 Java 경로는
`extensions/music/src/main/java/app/morphe/extension/music/`이다.

## 적용 및 사용

1. Java 21과 Android SDK 36 및 37.0, Build Tools 36.0.0을 준비한다.
   SDK 37은 저장소의 기존 change-version-code 모듈 빌드에 필요하다.
   저장소의 빌드 플러그인과 GitHub Packages 의존성을 받을 수 있는 환경에서 실행한다.

   ```sh
   ./gradlew :patches:buildAndroid :extensions:music:testDebugUnitTest \
     -PpatchBundleName="LyricBridge Patches" --no-daemon
   ```

2. Morphe Manager에서 생성된 `patches/build/libs`의 **패치 번들**을 사용자 지정
   번들로 불러온다. UI 명칭은 Manager 버전에 따라 다를 수 있다.
   YouTube Music APK를 선택하고 `AI synced lyrics translation`을 선택한다.
   의존성 `Third-party lyrics`도 포함되어야 한다. 기존 필수/권장 패치를 함께 적용한다.
   설치된 패치 APK를 수정하는 방식보다 원본 APK에 다시 적용한다.
3. 지원 APK: 안정 대상 **9.15.51, 9.20.53**; 실험 대상
   **9.38.51, 9.39.52, 9.40.51**. Android 8.0/API 26 이상.
   지원 목록은 `music/shared/Constants.kt`를 따른다.
4. YouTube Music → Morphe 설정 → 가사에서 `Third-party lyrics`를 활성화한다.
   별도의 **AI 동기화 가사 번역** 그룹에서 새 기능을 켠다.
5. AI 인증 방식에서 **ChatGPT 계정**을 선택한다.
   **ChatGPT 계정 연결 → ChatGPT로 계속하기**를 누른다.
   시스템 브라우저에서 공식 OpenAI 로그인/동의 화면을 완료하고 앱으로 돌아온다.
6. `로그인됨 · 요금제 사용 권한 미승인`이면 로그인만 완료된 상태다.
   계정 메뉴의 **ChatGPT 요금제 사용 권한 승인**으로 명시적 재동의를 요청한다.
   권한이 승인되어도 계정/워크스페이스/지역/모델의 지원 여부는 서버에서 판단한다.
7. **ChatGPT AI 모델 선택**을 누른다. 공식 `/v1/models`의 표시 가능 모델만
   서버 순서대로 표시한다. 계정을 바꾸면 목록을 다시 열어 모델을 선택한다.
8. 기본 대상 언어는 새 기능에만 **한국어**다. 자연스러운 번역/직역/의역,
   시스템 지침, 자동 번역 여부를 설정한다. 설정 변경 후 앱 재시작을 요구하는
   기존 Morphe 설정 흐름을 따른다.
9. 곡의 가사 화면에서 기존 번역 버튼을 누르거나 새 **AI** 버튼 메뉴에서
   **원문만 / 번역만 / 원문+번역 / 새로 생성**을 선택한다.
   기존 제공자 새로고침 버튼과 길게 누르는 가사 검색은 그대로 유지된다.
10. 대체 인증은 새 기능의 **API 키** 방식과 별도 HTTPS Chat Completions
    엔드포인트/API 키/모델 필드를 사용한다. 기존 OpenAI 호환 기능의 설정과
    구분되어 저장된다. 새 기능의 API 키는 설정 내보내기에서 제외된다.

## 인증과 안전한 세션 처리

- 공식 authorize/token/JWKS만 사용한다. 최초 `dynamic_agent_client`로 등록하고
  콜백에서 발급된 client ID를 사용해 코드를 교환한다.
- host ID는 설치별 `urn:uuid:`로 한 번 생성한다. 등록 정보는 계정/client ID별로
  보존하고, 등록된 계정 재로그인에는 같은 client ID와 host ID를 사용한다.
- 매 시도마다 256-bit 난수 state/nonce/verifier를 생성한다. PKCE는 S256이다.
- 브라우저 실행 전에 `127.0.0.1`의 사용 가능한 포트에 listener를 시작한다.
  콜백 경로는 `/auth/callback`이며 코드 교환에 정확히 같은 URI를 사용한다.
  localhost, 사용자 정의 URI, 웹사이트 쿠키 또는 ChatGPT backend API를 쓰지 않는다.
- state/콜백 중복 파라미터/client ID를 검증한 후 코드 교환을 진행한다.
  JWKS RS256 서명, issuer, audience/azp, exp/nbf/iat, nonce, sub를 검증한다.
  재로그인 때 다른 계정의 sub로 기존 등록을 덮어쓸 수 없다.
- 승인된 `scope`에서 `chatgpt.tokens.use.direct`를 확인한다.
  ID 토큰 자체나 ChatGPT 로그인 성공을 추론 권한으로 간주하지 않는다.
- access/refresh/retained ID token과 계정/client/host 정보는 Android Keystore의
  비수출 AES-256-GCM 키로 암호화한다. `noBackupFilesDir`의 AtomicFile로 저장한다.
  토큰, 인증 URL, 응답 본문 및 가사는 로그에 출력하지 않는다.
- 만료 60초 전부터 직렬화된 refresh grant를 수행한다. scope를 추가하지 않고,
  회전된 refresh token과 access token/expiry/scope를 함께 저장한 뒤 사용한다.
  회전형 token grant는 자동 재시도하지 않는다. terminal refresh 오류에서만
  쓸 수 없는 토큰을 지우고 기존 계정/client 매핑은 보존한다.
- 연결 해제 시 discovery의 revocation endpoint에 refresh token을 해제한다.
  서버 확인 실패 시 로컬 토큰을 지우고 사용자에게 ChatGPT 설정에서 연결을
  해제하도록 안내한다. 계정 선택/연결 해제는 진행 중 번역을 취소한다.

## 번역, 캐시와 오류 처리

- ChatGPT 경로는 `POST https://api.openai.com/v1/responses`, OAuth bearer,
  `store:false`, `stream:true`, `instructions`, 사용자 `input` 배열만 전송한다.
  temperature/max_output_tokens/metadata/previous_response_id 등 미지원 필드는 없다.
- SSE의 UTF-8, CRLF, 여러 data 줄, heartbeat, reasoning 이후 text output index를
  처리한다. `response.completed`를 받은 경우에만 번역 성공으로 판단한다.
  failed/incomplete/중단된 스트림이나 부분 번역은 표시/캐싱하지 않는다.
- 전체 가사를 한 요청에 넣어 제목·아티스트·후렴 문맥을 확보한다.
  1,200줄/80,000문자를 초과하는 입력은 안전하게 실패한다. 향후 장곡 분할은 별도 작업이다.
- 구간 ID는 원문 배열 위치와 SHA-256 텍스트 해시로 결정한다. 빈 줄과 반복 줄도
  별도 ID를 갖는다. 모델에 타임스탬프를 보내거나 생성시키지 않는다.
- JSON 응답에서 모든 ID가 정확히 한 번, 원래 순서로 존재해야 한다. 누락,
  중복, 순서 변경, 빈 번역, 여러 줄 텍스트 또는 후행 설명은 거부한다.
  실패 시 원문과 음악 재생을 유지한다. 번역 품질 자체는 모델의 출력에 의존한다.
- 캐시 키: 프로토콜/프롬프트 버전, 동영상 ID+곡 메타데이터, 제공자, 원문 배열,
  대상 언어, 모델, 모드, 시스템 지침, API 엔드포인트를 SHA-256으로 해시한다.
  원문/지침/모델/모드가 바뀌면 기존 캐시를 다시 사용하지 않는다.
- 앱 전용 디스크 캐시는 최대 200곡이며 파일을 원자적으로 저장한다.
  캐시 삭제 이후 이전 요청이 캐시를 다시 기록하지 못하도록 generation을 검증한다.
  캐시 조회/읽기/네트워크는 작업 스레드에서 수행한다.
- 동일 키의 진행 중 요청은 합쳐 처리한다. 곡 변경은 HTTP 연결과 Future를 취소한다.
  connect 10초/read 60초, 작업 deadline 180초와 제한된 5xx 재시도(0.5초/1초)를 적용한다.
  401/403/429/네트워크/응답 구조 오류를 구분한다. SSE 실패 이후 같은 추론을
  재시도하지 않는다. 원문 화면의 기존 요청 ID 검증도 유지한다.
- 가사는 선택한 AI 제공업체로만 전송한다. 제3의 공개 저장 서버는 추가하지 않는다.
  제공자의 가사 이용/전송/번역 조건을 확인하고 허용된 가사에 사용한다.

## 테스트와 실제 기기 검증

일반 개발 환경:

```sh
./gradlew :extensions:music:testDebugUnitTest
# GitHub 패키지 인증/Android SDK 없이 프로토콜 테스트만 수행:
./gradlew -p tools/ai-lyrics-tests test jar
```

Android Keystore 계측 테스트 소스도 포함한다. 대상은 별도의 확장 테스트 앱이며
사용자의 YouTube Music 데이터를 수정하지 않는다. Android 기기를 연결한 환경에서:

```sh
./gradlew :extensions:music:connectedDebugAndroidTest
```

독립 테스트 프로젝트는 **프로토콜 부분만** 컴파일한다. Android Keystore,
실제 인증 및 patched APK 동작을 증명하는 테스트는 아니다.
전체 빌드와 이 작업 환경의 구체적인 결과는 [검증 기록](VALIDATION.ko.md)을 참고한다.

실제 Android에서 아래 항목을 확인해야 한다:

- 지원 최소/최대 안정 APK와 실험 APK에서 원문·기존 기능 회귀 검증.
- Android 8/API 26과 최신 Android, Chrome/Firefox 시스템 브라우저에서
  dynamic 등록, 정확한 loopback 콜백, 재로그인과 취소/180초 만료.
- 브라우저가 localhost를 차단하는 설정/VPN, 외부 기기 브라우저 사용,
  앱 프로세스 종료 시 로그인 실패와 재시도. 브라우저는 같은 기기여야 한다.
- 로그인 중 음악 재생 유지, 계정 동의 거절, identity-only 상태, 지원되지 않는
  요금제/워크스페이스/지역, 실제 `/v1/models` 목록 및 첫 Responses 추론.
- 앱 재시작 후 Keystore 복호화 및 재생, 실제 만료/refresh token 회전,
  같은 계정/다른 워크스페이스 선택, ChatGPT에서 외부 해제 후 복구.
- 원문+번역/번역만/원문만 화면의 줄 강조, 자동 스크롤, seek, 듀엣/배경 보컬,
  재생 중 다음 곡 전환, 번역 새로 생성 및 네트워크 끊김.
- 캐시 재생 시 API 미호출, 모델/언어/모드/지침 변경 시 새 요청,
  번역 중 캐시 삭제, 연결 해제 후 spinner 종료.
- 로그와 설정 내보내기에 OAuth 토큰/인증 URL이 없고 백업에 credential 파일이
  포함되지 않는지 확인. API 키 대체 경로의 키는 앱 전용 설정에 저장된다.

## 알려진 제약

공식 OSS 문서는 loopback public-client 흐름을 제시하며 확인한 문서에는 Android를
명시적으로 금지하는 항목이 없다. 그러나 Android 실제 브라우저/프로세스 생존과
OpenAI의 이 앱에 대한 실제 dynamic 등록·추론 허용은 계정과 기기 없이는 검증할 수 없다.
본 코드 구현 또는 JVM 테스트 통과를 실기기 로그인 성공으로 보고하지 않는다.

로그인 시도는 메모리에만 유지한다. 브라우저로 전환한 동안 앱 프로세스가 종료되면
시도를 다시 시작해야 한다. token hint는 공식 authorize URL에만 전달한다.
Keystore 사용 불가/키 손상 시 저장소 오류를 표시하며 암호화를 우회하지 않는다.
단어 단위 번역 싱크, 번역 의미의 자동 품질 평가, 장곡 분할 및 라이브러리 수준의
모든 가사 제공자 라이선스 검증은 이번 범위에 포함하지 않는다.

## 공식 문서

- https://developers.openai.com/siwc/token-sharing-open-source
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions
- https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations
- https://developers.openai.com/siwc/token-sharing-open-source/token-reference
- https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery
