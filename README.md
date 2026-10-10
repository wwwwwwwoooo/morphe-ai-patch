# LyricBridge Patches

Morphe YouTube Music에 ChatGPT 계정 연결과 동기화 AI 가사 번역을 추가한 사용자 지정 패치 저장소입니다. [Morphe Patches](https://github.com/MorpheApp/morphe-patches) v1.46.0을 기반으로 하며, 기존 가사 제공자와 번역 기능을 유지합니다. Morphe 또는 OpenAI의 공식 배포판이 아닙니다.

새 기능은 기본 선택 OFF인 **`AI synced lyrics translation`** 패치입니다. `Third-party lyrics`와 함께 적용합니다.

- 공식 Sign in with ChatGPT: PKCE, loopback 콜백, ID 토큰 검증, Android Keystore 토큰 저장.
- 공식 모델 목록과 Responses API 스트리밍 번역. 별도 API 키 방식도 선택 가능합니다.
- 원문/번역/함께 보기, 번역 새로고침, 자연스러운 번역/직역/의역, 편집 가능한 프롬프트.
- 원래 타임스탬프 유지, 줄 ID 대응 검증, 취소 및 별도 로컬 캐시.

## 다운로드와 적용

[Releases](https://github.com/wwwwwwwoooo/morphe-ai-patch/releases)에서 `.mpp` 패치 번들을 다운로드하고 Morphe Manager의 사용자 지정 번들로 불러옵니다. YouTube Music 원본 APK에 `AI synced lyrics translation`과 의존성 `Third-party lyrics`를 적용합니다. 번들은 설치용 YouTube Music APK가 아닙니다.

Android 8.0 이상, 안정 대상 YouTube Music **9.15.51 / 9.20.53**, 실험 대상 **9.38.51 / 9.39.52 / 9.40.51**을 지원 목록으로 사용합니다.

[설치·로그인·번역 사용법 및 기기 검증 목록](docs/ai-lyrics/README.ko.md) · [실행한 테스트와 빌드 기록](docs/ai-lyrics/VALIDATION.ko.md)

## 검증 상태

Gradle 패치 번들 빌드와 JVM 단위 테스트 **18개**가 통과했습니다. Android 계측 테스트 3개 및 테스트 APK를 작성·빌드했습니다.

**실제 ChatGPT 계정 로그인, 요금제 추론 권한, Android 기기의 패치 APK 동작과 가사 동기화는 아직 실행하지 않았습니다.** 계측 테스트도 기기에서 실행하지 않았습니다. 로그인 성공과 요금제 사용 권한은 별개이며, 서비스의 승인 범위에 따라 추론이 제한될 수 있습니다.

## 소스에서 빌드

Java 21, Android SDK 36 및 37.0, 원본 프로젝트의 GitHub Packages 의존성 접근 설정이 필요합니다.

```sh
./gradlew :patches:buildAndroid :extensions:music:testDebugUnitTest -PpatchBundleName="LyricBridge Patches" --no-daemon
```

Android SDK 없이 프로토콜 단위 테스트만 실행:

```sh
./gradlew -p tools/ai-lyrics-tests test --no-daemon
```

초기 코드 업로드 커밋은 `[skip ci]`로 CI 실행을 건너뜁니다. 원본의 워크플로 파일은 보존되어 있으며, 이후 업로드나 수동 실행 전에 사용자 지정 배포에 맞는 설정을 검토하세요. 자세한 빌드 환경과 검증 한계는 위 문서를 참고하세요.

## 라이선스와 출처

[LICENSE](LICENSE)와 [NOTICE](NOTICE)를 유지합니다. 원본 Morphe 및 ReVanced 기여와 변경 이력은 Git 기록에 보존되어 있습니다. 사용자 지정 배포 이름은 **LyricBridge Patches**입니다. 원본 프로젝트의 설명은 [상위 저장소](https://github.com/MorpheApp/morphe-patches)를 참고하세요.
