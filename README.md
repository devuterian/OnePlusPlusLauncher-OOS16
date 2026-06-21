<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="180" alt="SearchUp 아이콘" />

# SearchUp

### 위로 올리면 바로 검색하고, 엔터로 앱을 여는 LSPosed 모듈

**한국어** | [English](README.en.md) | [日本語](README.ja.md)

</div>

SearchUp은 OnePlus OxygenOS 16 System Launcher의 앱 서랍을 빠른 검색 화면처럼 쓰게 해주는 LSPosed 모듈입니다.

홈 화면에서 위로 올리면 검색창에 바로 포커스가 가고 키보드가 떠요. 한글 초성 검색과 엔터 실행도 같이 넣어서, 앱 서랍을 열고 다시 검색창을 누르는 일을 줄이는 쪽에 집중했습니다.

원본은 [OnePlusPlusLauncher](https://github.com/wizpizz/OnePlusPlusLauncher)이고, 이 저장소는 OxygenOS 16 대응과 SearchUp 흐름을 얹은 fork입니다.

## 기능

- 위로 올려 앱 서랍에 들어가면 검색창에 자동 포커스와 키보드 표시
- 한글/영문 검색 개선, 한글 초성 검색 지원
- 키보드 엔터로 첫 번째 검색 결과 실행
- 검색 버튼, 아래 스와이프, 왼쪽 Discover 진입을 앱 서랍 검색으로 리다이렉트
- 검색 화면에서 뒤로가기를 눌렀을 때 키보드와 검색 상태 정리
- 설정 변경 후 System Launcher를 앱 안에서 재시작
- 앱 설정 화면에서 한국어/영어/중국어 간단 전환

## 호환

- 확인한 기기 기준: OnePlus 13 / OxygenOS 16
- 확인한 런처 기준: System Launcher `16.6.5`
- LSPosed scope: `com.android.launcher`
- 앱 패키지: `com.wizpizz.onepluspluslauncher`
- 모듈 버전: `1.0.0-oos16.0.7.201`
- 최소 Android SDK: `27`
- 대상 Android SDK: `35`
- Xposed 최소 버전: `93`

**주의:** 루팅과 LSPosed가 필요합니다. 런처 업데이트로 내부 클래스가 바뀌면 훅이 일부 또는 전부 안 먹을 수 있어요. 그럴 땐 새 APK를 올리기 전까지 기존 동작으로 돌아가는 게 정상입니다.

## 설치

1. [Releases](https://github.com/devuterian/OnePlusPlusLauncher-OOS16/releases)에서 최신 APK를 받습니다.
2. APK를 설치합니다.
3. LSPosed에서 SearchUp 모듈을 켭니다.
4. scope에 System Launcher, 즉 `com.android.launcher`를 추가합니다.
5. SearchUp 앱에서 `변경 적용: 런처 재시작`을 누르거나, 기기를 재부팅합니다.

설치가 끝났는데 동작하지 않으면 LSPosed에서 모듈 활성화와 scope를 먼저 확인해 주세요. 이 둘이 빠지면 앱을 켜도 런처에는 아무 일도 안 일어납니다.

## 사용법

기본 흐름은 단순합니다.

1. 홈 화면에서 위로 올립니다.
2. 키보드가 뜨면 바로 앱 이름을 입력합니다.
3. 원하는 앱이 첫 번째에 있으면 엔터를 누릅니다.

설정 화면의 `SearchUp 기본 흐름`은 보통 켜두면 됩니다. `키보드 동작 (고급)`과 `검색 이동 (고급)`은 검색 버튼, 아래 스와이프, Discover 리다이렉트까지 바꾸고 싶을 때만 건드리세요.

## 빌드

이 프로젝트는 Gradle Kotlin DSL을 씁니다. 로컬 JDK는 17 기준입니다.

```sh
./gradlew test assembleRelease
```

결과물은 기본 Gradle 출력 경로에 생깁니다.

- release APK: `app/build/outputs/apk/release/app-release.apk`
- debug APK: `app/build/outputs/apk/debug/app-debug.apk`

release 서명은 환경 변수나 로컬 `.env`에서 읽습니다. 이 파일은 저장소에 넣지 마세요.

```text
SIGNING_KEY_STORE_PATH=
SIGNING_KEY_ALIAS=
SIGNING_KEY_STORE_PASSWORD=
SIGNING_KEY_PASSWORD=
```

서명 값이 없으면 release signing을 건너뜁니다. 배포용 APK를 만들 거면 값을 채워야 합니다.

## 문제 해결

- 모듈 상태가 `아직 활성화되지 않았습니다`로 보이면 LSPosed에서 SearchUp을 켜세요.
- 기능을 켰는데 바로 반영되지 않으면 SearchUp 앱의 런처 재시작 버튼을 누르세요.
- 런처 업데이트 뒤에 검색 훅이 깨지면, 확인된 System Launcher 버전과 release notes를 먼저 비교하세요.
- 꼬였을 때는 LSPosed에서 모듈을 끄고 System Launcher를 재시작하면 원래 런처 동작으로 돌아갑니다.

## 라이선스

이 저장소는 `AGPL-3.0` 라이선스를 따릅니다. 자세한 내용은 [LICENSE](LICENSE)를 확인해 주세요.

## 크레딧

- 원본 모듈: [wizpizz/OnePlusPlusLauncher](https://github.com/wizpizz/OnePlusPlusLauncher)
- OxygenOS 16 adaptation: [zhangbaoshengrio/OnePlusPlusLauncher-OOS16](https://github.com/zhangbaoshengrio/OnePlusPlusLauncher-OOS16)
- SearchUp 유지 fork: [devuterian/OnePlusPlusLauncher-OOS16](https://github.com/devuterian/OnePlusPlusLauncher-OOS16)
- Hook framework: [YukiHookAPI](https://github.com/HighCapable/YuKiHookAPI)
