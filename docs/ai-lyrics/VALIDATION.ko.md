# 구현 및 검증 기록

검증 날짜: 2026-10-10 UTC. 기반 저장소 v1.46.0 (`1bcd0bced`).
처음 클론한 작업 트리는 깨끗했으며 사용자 변경 사항을 삭제하지 않았다.
PR #3372의 merge commit이 HEAD의 조상인 것을 확인했다.

## 실제 실행한 검증

| 항목 | 결과 |
|---|---|
| Android music 모듈 `testDebugUnitTest` | 18 tests, 실패 0, 오류 0, skip 0 |
| 독립 프로토콜 프로젝트 `test jar` | 같은 18 tests, 실패 0, 오류 0, skip 0; Gradle 성공 |
| `:patches:buildAndroid` | Gradle 성공; Kotlin/Java 컴파일, R8 및 D8 완료 |
| 번들 `loadPatchesFromJar`/목록 생성 | 성공; 새 패치 이름, default=false, 패키지/버전 목록 확인 |
| 번들 내부 검사 | 새 Kotlin 패치 class, classes.dex, music.mpe 내 OAuth/Responses 코드 확인 |
| 원본 기능 소스 비교 | 기존 OpenAIClient, LyricsCache, LyricsAiConfigPreference 변경 없음 |
| 기본/한국어 리소스 XML | 파싱 성공, 추가 리소스 중복 없음 |
| `git diff --check` | 성공 |
| Android 계측 APK 및 별도 테스트 대상 APK 빌드 | 성공; 테스트 3개 작성, 기기 실행은 미검증 |

18개 테스트의 주요 검증 대상:

- RFC 7636 PKCE S256 벡터, state 불일치 및 consent 오류 순서.
- dynamic 등록의 issued client ID, 재로그인 client ID 불일치,
  정확한 redirect/resource 및 중복 콜백 파라미터 거부.
- 생성한 RSA 키로 실제 JWT 서명 검증; 잘못된 서명, issuer, audience,
  azp, 만료, nonce, 계정 sub 거부.
- 실제 JVM loopback socket의 잘못된 state/경로 거부와 성공 콜백,
  listener 종료 및 취소.
- 공식 refresh grant 필드, scope 미확대, 토큰 교체 및 이전 record 보존,
  회전형 refresh grant 미재시도, terminal 오류 분류.
- SSE UTF-8/BOM/CRLF/여러 data 줄/heartbeat/reasoning output index,
  완료 전 부분 응답 및 failed/incomplete/중단 거부.
- Responses 요청 필드 allowlist, 제한/권한/인증/일시 오류 구분.
- 반복/빈 줄 ID, 구간 누락/중복/순서 변경/여러 줄 번역/후행 설명 거부.
- 원문/곡/언어/모델/모드/프롬프트 변경에 따른 캐시 키 변경,
  파일 손상 및 캐시 삭제 뒤 stale writer 거부.

## 빌드 환경과 재현

이 환경에는 시작 시 Android SDK와 javac 실행 파일, GitHub Packages 인증이 없었다.
Gradle/Android의 기본 사용자 경로도 읽기 전용이었다. 다음으로 해결했다:

- 작업 공간에 OpenJDK 21.0.2, Gradle 9.8.0, SDK 36 및 37.0 설치.
- `GRADLE_USER_HOME=/workspace/.gradle`, `ANDROID_USER_HOME=/workspace/.android` 사용.
- 세션 프록시를 유지하고 JVM에 프록시와 `/etc/ssl/certs/java/cacerts`를 지정.
  TLS 검증을 끄지 않았다.
- 공식 `morphe-patcher` v1.14.0과 `morphe-patches-library` v1.8.0-dev.2를
  인접 디렉터리에 클론하여 저장소의 기존 composite build 기능으로 사용.
- `morphe-patches-gradle-plugin` 1.3.4 소스를 includeBuild로 사용.
  해당 로컬 빌드 지원 소스의 nullable GitHub 환경변수 기본값만 빈 문자열로
  처리했다. 인증을 우회하거나 개인 토큰을 추가하지 않았다.
- SDK 37.0은 기존 change-version-code 확장 모듈 때문에 필요했다.
  공개 SDK 저장소 메타데이터와 일치하는 실제 플랫폼을 설치했다.

일반적으로 GitHub Packages 접근이 구성된 개발 환경에서는 사용 설명서의
원래 Gradle 명령으로 빌드한다. 이 세션의 source build init script는
`/workspace/research/source-build.init.gradle`이다. 본체 저장소에 이러한
환경 임시 경로나 자격 증명을 하드코딩하지 않았다.

실행한 주요 task:

```text
:patches:buildAndroid :extensions:music:testDebugUnitTest
-PpatchBundleName=LyricBridge Patches --no-daemon --max-workers=2

-p tools/ai-lyrics-tests test jar --no-daemon --max-workers=2

:patches:verifyAiBundle  (환경 전용 init script의 JavaExec task)
```

## 산출물

- Android 패치 번들: `patches/build/libs/patches-1.46.0.mpp`.
- music 확장 DEX: `extensions/music/build/morphe/extensions/music.mpe`.
- 프로토콜 JVM jar: `tools/ai-lyrics-tests/build/libs/lyricbridge-protocol-tests.jar`.
  이 jar는 Android 패치 번들이 아니다.
- 계측 테스트 APK: `extensions/music/build/outputs/apk/androidTest/debug/music-debug-androidTest.apk`.
- 별도 테스트 대상 APK: `extensions/music/build/outputs/apk/debug/music-debug.apk`.
  이 두 APK는 YouTube Music 앱이나 패치 번들이 아니라 계측 테스트용이다.
- Android 테스트 결과: `extensions/music/build/test-results/testDebugUnitTest/`.
- 독립 테스트 HTML: `tools/ai-lyrics-tests/build/reports/tests/test/index.html`.
- 배포 묶음: `/workspace/artifacts/lyricbridge-lyrics-1.46.0.zip`.
  custom .mpp, 소스 diff, 설명서, 검증 로그/XML, LICENSE/NOTICE를 포함한다.

## 아직 검증하지 않은 부분

실제 ChatGPT 계정이나 원본 YouTube Music APK가 제공되지 않았고 Android 기기
실행 환경이 없어서 **실제 dynamic 등록/계정 로그인/모델 목록/유료 요금제 추론,
실제 회전형 토큰 갱신/해제, patched APK의 화면·재생·동기화는 실행하지 않았다**.
번들을 실제 지원 APK에 적용하는 통합 검증도 하지 않았다.

Android 계측 테스트 `AndroidSessionTest`는 Keystore round-trip/암호문 변조 거부/
Android loopback 콜백을 위한 코드다. 테스트 APK 컴파일과 실제 기기 실행은
서로 다른 검증이다. 위 두 테스트 APK는 빌드했으나 실제 실행이 없으므로
계측 테스트 3개가 통과했다고 간주하지 않는다.
실제 기기 검증 항목은 README.ko.md에 정리했다.
