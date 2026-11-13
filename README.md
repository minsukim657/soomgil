# 숨길 · Soomgil 0.3

미세먼지와 도로 분진에 민감한 탑승자를 위한 차량 공조 앱입니다. 첫 시연은 등록한 경로에서 진행 중인 공사 구간에 접근하고 인근 초미세먼지가 `나쁨`일 때, 진입 전 내기순환을 요청하는 흐름을 재현합니다. 앱이 내기순환을 켠 경우 공사 위험과 대기질 위험이 모두 해소된 뒤에만 출발 전 OFF 상태로 복원합니다. 출발 전 이미 내기순환 ON이면 그 상태를 유지합니다.

## 실행

1. Android Studio에서 이 폴더를 엽니다. Android SDK 36을 사용해 빌드하며, 실행 대상은 API 28 이상입니다.
2. Android 에뮬레이터를 선택하고 `app`을 실행합니다. 빌드된 `build/manual/Soomgil-demo.apk`가 있으면 에뮬레이터에 끌어 놓아 설치할 수도 있습니다.
3. **이 조건으로 주행 시작**을 누르면 약 3초마다 자동으로 진입 전, 통과 중, 통과 후로 이동합니다.
4. `공사 진행 · 접근 시 대기 나쁨`과 `공사 종료 · 같은 도로와 대기 등급`을 각각 실행해 비교합니다. 공사나 대기 정보를 확인할 수 없는 시나리오에서는 새 전환을 시작하지 않습니다. 통과 후에도 대기가 나쁜 시나리오에서는 내기순환을 유지하고, 공사와 대기질 위험이 모두 해소된 경우에만 OFF로 복원하는지 확인합니다. **운전자 공조 조작**과 **앞유리 김 서림 제거**는 해당 주행에서 앱 자동 제어보다 우선합니다.

## 시제품 범위

- 도로 접근·통과 단계는 시연 데이터입니다. Pleos 내기순환 ON/OFF는 실제 차량에서 읽고 제어하며, 1.5초 간격으로 화면을 동기화합니다. SET 후 최대 5회 상태 재조회로 실제 반영을 확인합니다.
- `VentilationPolicy`가 관찰값과 현재 공조 모드를 받아 이유를 포함한 `SWITCH / RESTORE / KEEP` 판단을 반환합니다. `MainActivity`는 가상 주행 관찰값을 넣고 결과를 화면에 표시합니다.
- 실제 공사·대기 조회는 `진행/종료/확인 불가`와 `보통/나쁨/확인 불가`로 정규화합니다. 조회 실패, 오래된 관측, 구간 일치 여부가 불확실한 경우는 `확인 불가`입니다. 대상 방향과 도로가 일치할 때만 `APPROACH`를 전달합니다.
- Pleos의 실제 내기순환 Boolean을 화면에 표시합니다. 운전자 직접 조작이 감지되면 해당 주행에서 자동 제어권을 포기하며 운전자 설정을 되돌리지 않습니다. SDK에서 전체 HVAC 프로필을 읽는 것은 아니므로 `기본 공조`는 내기순환 OFF 상태를 뜻합니다.

## 실제 Pleos 연동 순서

1. Android Studio의 **Tools → SDK Manager → SDK Update Sites**에서 [Pleos 전용 시스템 이미지](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)를 추가합니다. Windows용 주소는 `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`입니다. **API 34 / Pleos Connect v2.1.0 / Cold boot / RAM 4 GB**로 기기를 생성합니다. [에뮬레이터 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)
2. 기기를 부팅하고 `adb forward tcp:8088 tcp:8088`을 실행한 뒤 브라우저에서 `http://127.0.0.1:8088/`의 **Climate**를 엽니다. [Virtual Console](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
3. Pleos Playground의 프로젝트 SDK 화면에서 Vehicle Control과 FusedLocation 사용 상태를 확인합니다. 공개 안내는 둘을 **준비 중**으로 표시합니다. [공식 Vehicle 샘플](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app)의 HVAC 화면에서 지원 여부와 권한을 확인하고, 공조 상태 조회 → 내기순환 변경 요청 → 실제 상태 콜백까지 확인합니다. 시제품은 현재 이 검증 전 단계입니다. [SDK 사용 상태 안내](https://document.pleos.ai/docs/pleos-only/create-my-project) · [HVAC API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/entry)
4. [FusedLocation](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)의 위치와 진행 방향을 등록 경로에 대조합니다. 실제 내비 경로 전체와의 연동은 [NaviHelper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)가 제공하는 경로 데이터를 검증한 뒤 확장합니다.
5. [ITS 공사 정보](https://www.its.go.kr/file/opendata/openapi_manual.pdf)와 [에어코리아 측정소 정보](https://www.data.go.kr/data/15073861/openapi.do)를 연결합니다. 공사 방향과 유효 시간을 확인할 수 있는 구간 하나와 측정소 하나를 먼저 선택합니다. 대기 등급은 인근 측정소의 관측값으로 표시합니다. API 키는 저장소와 공개 APK 밖에서 관리합니다.

## 공모전 제출 기준

- **2026년 11월 15일 1차 접수 마감**. 접수양식과 팀원 전원의 서명·동의를 준비합니다. 영상과 GitHub·APK는 선택 제출입니다. [공모전 일정](https://appdevchallenge.auto-hmg.io/guide/schedule) · [제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission)
- GitHub 산출물로 제출할 때는 실행을 확인한 release APK를 `apk/팀명_앱이름_release.apk` 경로에 넣습니다. 비공개 저장소의 경우 공지된 심사 계정의 접근을 확인합니다. `Soomgil-demo.apk`는 가상 입력으로 구동하는 내부 시연용입니다. [제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission)
- **2026년 12월 3~4일 2차 PT**. 실제 동작 가능한 앱 시연을 준비합니다. [심사·시상](https://appdevchallenge.auto-hmg.io/guide/judging-awards)

## 자동 전환 정책 테스트 및 연동

JDK 17 이상에서 `sh scripts/test-ventilation.sh`로 Android SDK 없이 정책 및 위치 분류 회귀 테스트를 실행합니다. 상세 판단 계약, 테스트 범위, 아직 확정하지 않은 외부 인터페이스는 [공조 정책 연동 문서](docs/ventilation.md)를 참고하세요.


## ITS 및 에어코리아 API 시연

앱의 **공사·주변 대기 관측** 카드에서 **실제 API에서 조회**를 누르면 시연 좌표 주변의 공사 한 건과 가까운 에어코리아 측정소의 최신 초미세먼지 등급을 조회해 화면에 표시합니다. 이 응답은 아직 가상 주행 단계/자동 정책에 연결되지 않습니다. 결과에는 공사 상태·도로와 구간·방향·유효 시각, 측정소명·등급·관측 시각이 포함됩니다. 샘플 메뉴에서 **공사 진행**, **공사 종료**, **조회 실패**를 고르고 **샘플로 주행 시작**을 누르면 같은 샘플이 조회 카드와 자동 주행 판단에 사용됩니다.

실제 조회를 사용하려면 프로젝트 루트의 로컬 `local.properties`에 아래 두 속성을 지정하세요. 이 파일은 `.gitignore`에서 제외되어 있습니다.

```properties
ITS_API_KEY=발급받은_ITS_키
AIRKOREA_API_KEY=발급받은_에어코리아_키
```

API 키는 소스 저장소에 포함하지 마세요. 로컬 키는 APK에 포함되어 추출될 수 있으므로 배포 시에는 서버 중계 또는 키 제한을 적용해야 합니다.
