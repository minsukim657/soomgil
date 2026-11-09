# 숨길 · Soomgil 0.2

미세먼지와 도로 분진에 민감한 탑승자를 위한 차량 공조 앱입니다. 첫 시연은 등록한 경로에서 진행 중인 공사 구간에 접근하고 인근 초미세먼지가 `나쁨`일 때, 진입 전 내기순환을 요청하는 흐름을 재현합니다. 구간 통과 후 인근 관측 등급이 `보통`이면 이전 공조 설정을 복원합니다.

## 실행

1. Android Studio에서 이 폴더를 엽니다. Android SDK 36을 사용해 빌드하며, 실행 대상은 API 28 이상입니다.
2. Android 에뮬레이터를 선택하고 `app`을 실행합니다. 빌드된 `build/manual/Soomgil-demo.apk`가 있으면 에뮬레이터에 끌어 놓아 설치할 수도 있습니다.
3. **이 조건으로 주행 시작**을 누르고 **다음 구간**을 눌러 진입 전, 통과 중, 통과 후 상태를 확인합니다.
4. `공사 진행 · 접근 시 대기 나쁨`과 `공사 종료 · 같은 도로와 대기 등급`을 각각 실행해 비교합니다. 공사나 대기 정보를 확인할 수 없는 시나리오에서는 새 전환을 시작하지 않습니다. 통과 후에도 대기가 나쁘면 **시간 경과 시연**으로 자동 제어 종료를 확인할 수 있습니다. **운전자 공조 조작**과 **앞유리 김 서림 제거**는 해당 주행에서 앱 자동 제어보다 우선합니다.

## 시제품 범위

- 위치, 도로 공사 상태, 주변 대기 등급, 공조 상태는 시연 데이터입니다. 시간 경과는 버튼으로 진행하며, 45초는 시연을 위한 설정입니다.
- `VentilationPolicy`가 관찰값을 받아 공조 변경 의도를 반환합니다. `MainActivity`는 가상 주행 관찰값을 넣고 결과를 화면에 표시합니다.
- 실제 공사·대기 조회는 `진행/종료/확인 불가`와 `보통/나쁨/확인 불가`로 정규화합니다. 조회 실패, 오래된 관측, 구간 일치 여부가 불확실한 경우는 `확인 불가`입니다. 대상 방향과 도로가 일치할 때만 `APPROACH`를 전달합니다.
- 실제 환경에서 전환 명령의 성공 여부는 차량 응답과 상태 변경 콜백으로 확인해야 합니다. 시제품의 `RELEASE_TO_VEHICLE`은 제어권을 넘긴다는 의도이며, 대응하는 Pleos 단일 호출이 확인된 상태가 아닙니다. 실차에서는 기존 자동 환기 설정 복원과 직접 조작을 덮어쓰지 않는 동작을 먼저 검증해야 합니다.

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
