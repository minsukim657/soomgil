# 숨길 · Soomgil 0.3

미세먼지와 도로 분진에 민감한 탑승자를 위한 차량 공조 앱입니다. 자동 공조가 켜져 있으면 진행 중인 공사 구간에 접근하거나 초미세먼지가 나쁨 이상일 때 내기순환을 켭니다. 두 조건이 해소되면 앱이 변경한 내기순환을 운전자 설정으로 돌립니다.

## 실행

1. Android Studio에서 이 폴더를 엽니다. Android SDK 36을 사용해 빌드하며, 실행 대상은 API 28 이상입니다.
2. Android 에뮬레이터를 선택하고 `app`을 실행합니다. 빌드된 `build/manual/Soomgil-demo.apk`가 있으면 에뮬레이터에 끌어 놓아 설치할 수도 있습니다.
3. **이 조건으로 주행 시작**을 누르면 약 3초마다 자동으로 진입 전, 통과 중, 통과 후로 이동합니다.
4. **공사 진행 · 접근 시 대기 보통**에서는 공사 접근 시 ON, 통과 후 복구를 확인합니다. **공사 진행 · 통과 후에도 대기 나쁨**에서는 통과 후에도 ON을 유지하며 자동 전환 스위치가 켜져 있으면 직접 끄더라도 다시 켭니다.
5. 실제 연동은 **실제 GPS·환경으로 주행 시작**을 누릅니다. 앱이 ITS·에어코리아를 다시 조회하고 Pleos FusedLocation으로 받은 위치를 경로와 공사 지점에 대조합니다. 위치 권한이 필요하면 허용합니다.

## 시제품 범위

- 샘플 주행은 시연 단계로 도로 접근·통과를 재현합니다. 실제 GPS 주행은 Pleos FusedLocation의 위치와 진행 방향을 사용합니다. Pleos 내기순환 ON/OFF는 실제 차량에서 읽고 제어하며, 1.5초 간격으로 화면을 동기화합니다. SET 후 최대 5회 상태 재조회로 실제 반영을 확인합니다.
- `VentilationPolicy`가 관찰값과 현재 공조 모드를 받아 이유를 포함한 `SWITCH / RESTORE / KEEP` 판단을 반환합니다. 샘플 주행에는 시연 관찰값을, 실제 GPS 주행에는 API 환경 정보와 경로 일치가 확인된 실제 위치를 넣습니다.
- 실제 공사·대기 조회는 `진행/종료/확인 불가`와 `보통/나쁨/확인 불가`로 정규화합니다. 조회 실패, 2시간보다 오래된 관측, 구간 일치 여부가 불확실한 경우는 자동 제어 근거로 사용하지 않습니다. 대상 도로·좌표·방향과 실제 차량 진행 방향이 일치할 때만 `APPROACH`를 전달합니다.
- 차량의 실제 내기순환 ON/OFF를 표시합니다. 위험이 확인되면 자동 제어가 우선합니다. 위험이 없거나 정보 부족으로 판단을 보류할 때 운전자가 직접 바꾸면 그 상태를 기억합니다. 정보가 돌아오고 위험이 없으면 마지막 운전자 설정을 유지합니다.

## 제출 시연에서 보여주는 동작

숨길은 **서부간선도로 신정교~목동교 방면**의 등록 경로에서 공사와 대기질을 확인해 내기순환을 자동으로 조절하는 시제품입니다.

- **실제 데이터 연동 시연**에서는 ITS 공사 정보와 인근 에어코리아 측정소의 초미세먼지 등급을 실시간으로 조회합니다. Pleos 에뮬레이터에서 GPS 경로를 재생하면 앱이 공사 구간의 접근·통과를 판단합니다. 공사 구간에 접근하거나 초미세먼지가 나쁨 이상이면 내기순환을 켜고, 통과 후에도 대기가 나쁘면 유지합니다.
- **샘플 주행**에서는 공사 구간을 통과한 뒤 대기질이 보통으로 회복되는 상황을 재현합니다. 앱이 켰던 내기순환이 출발 전 OFF 상태로 돌아오는 과정을 실제 API의 관측 시점과 무관하게 보여줍니다.

이번 시연은 **공사 구간에 들어가기 전에 공조가 작동하는지** 확인하는 데 초점을 맞췄습니다. 경로를 미리 등록해 두면 GPS 위치를 해당 경로와 바로 대조할 수 있어 별도 길찾기 API나 API 키가 필요하지 않습니다. 목적지를 자유롭게 설정하고 그때마다 새 경로의 공사 구간을 판단하는 기능으로 확장하려면 주행 경로 좌표를 제공하는 길찾기 연동이 필요합니다.


## 서부간선도로 GPS 시연

1. Android Studio에서 Pleos Connect 에뮬레이터를 선택해 `app`을 실행합니다. Virtual Console을 열어 둔 상태에서 **Climate**의 내기순환을 끄고, 숨길에서 **기본 공조 중**이 표시되는지 확인합니다.
2. 에뮬레이터의 **Extended controls → Location**에서 [시연 GPX](demo/soomgil-seobu-demo.gpx)를 불러옵니다. 경로 첫 좌표가 `37.5152643, 126.881747`인지 확인합니다.
3. 숨길에서 **실제 GPS·환경으로 주행 시작**을 누릅니다. 앱이 ITS와 에어코리아를 새로 조회한 뒤 차량 위치를 받기 시작합니다.
4. GPX 경로를 재생하고 공사 지점에 접근·통과할 때의 판정과 Virtual Console의 내기순환 상태를 확인합니다. 대기 등급이 보통이어도 활성 공사에 접근하면 내기순환을 켭니다. 통과 후 대기질도 보통이면 앱이 변경한 상태를 복원합니다.

이 경로는 [서울경찰청 교통통제 공지](https://www.spatic.go.kr/spatic/assem/getInfoView.do?mgrSeq=2317)를 참고해 구성했습니다. 차도 좌표는 [OpenStreetMap 기여자](https://www.openstreetmap.org/copyright)가 제공했습니다. ITS 공사 좌표를 경로 중심선 30m 이내, 시연 경로 구간 내에서 찾고 북행 또는 양방향 정보가 확인된 기록만 사용합니다. API 좌표는 공사 지점의 근사값으로 취급해 그 지점을 중심으로 짧은 작업 구간을 만듭니다. 실제 내비 목적지 경로 전체의 자동 맵매칭은 아직 연결하지 않았습니다.

## 실제 Pleos 연동 순서

1. Android Studio의 **Tools → SDK Manager → SDK Update Sites**에서 [Pleos 전용 시스템 이미지](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)를 추가합니다. Windows용 주소는 `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`입니다. **API 34 / Pleos Connect v2.1.0 / Cold boot / RAM 4 GB**로 기기를 생성합니다. [에뮬레이터 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)
2. 기기를 부팅하고 `adb forward tcp:8088 tcp:8088`을 실행한 뒤 브라우저에서 `http://127.0.0.1:8088/`의 **Climate**를 엽니다. [Virtual Console](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
3. Pleos Playground의 프로젝트 SDK 화면에서 Vehicle Control과 FusedLocation 사용 상태를 확인합니다. 공개 안내는 둘을 **준비 중**으로 표시합니다. [공식 Vehicle 샘플](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app)의 HVAC 화면에서 지원 여부와 권한을 확인하고, 공조 상태 조회 → 내기순환 변경 요청 → 실제 상태 콜백까지 확인합니다. Pleos 에뮬레이터에서 GPS 시연의 내기순환 전환과 복원을 확인했습니다. [SDK 사용 상태 안내](https://document.pleos.ai/docs/pleos-only/create-my-project) · [HVAC API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/entry)
4. [FusedLocation](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)의 위치와 진행 방향을 등록 경로에 대조합니다. 임의 목적지 경로 연동에는 전체 경로 좌표를 제공하는 서비스가 필요합니다.
5. [ITS 공사 정보](https://www.its.go.kr/file/opendata/openapi_manual.pdf)와 [에어코리아 측정소 정보](https://www.data.go.kr/data/15073861/openapi.do)를 조회합니다. 공사 방향·좌표를 정적 시연 경로와 대조하고 인근 측정소의 초미세먼지 관측값을 자동 주행 정책에 전달합니다. 실제 GPS 주행 중 환경 정보를 5분마다 갱신합니다.

## 공모전 제출 기준

[시연 순서와 발표 대본](docs/demo-guide.md)을 참고하세요.

- **2026년 11월 15일 1차 접수 마감**. 접수양식과 팀원 전원의 서명·동의를 준비합니다. 영상과 GitHub·APK는 선택 제출입니다. [공모전 일정](https://appdevchallenge.auto-hmg.io/guide/schedule) · [제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission)
- GitHub 산출물로 제출할 때는 실행을 확인한 release APK를 `apk/팀명_앱이름_release.apk` 경로에 넣습니다. 접수 메일에 저장소 주소와 최종 커밋 해시를 적습니다. 공식 안내에 따르면 비공개 저장소는 11월 16일까지 공개로 전환해 11월 23일까지 유지하거나, 공개가 어려우면 접수 마감 전 소스와 APK 압축본 다운로드 주소를 제출할 수 있습니다. `Soomgil-demo.apk`는 가상 입력으로 구동하는 내부 시연용입니다. [제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission)
- **2026년 12월 3~4일 2차 PT**. 실제 동작 가능한 앱 시연을 준비합니다. [심사·시상](https://appdevchallenge.auto-hmg.io/guide/judging-awards)

## 자동 전환 정책 테스트 및 연동

JDK 17 이상에서 `sh scripts/test-ventilation.sh`로 Android SDK 없이 정책 및 위치 분류 회귀 테스트를 실행합니다. 상세 판단 계약, 테스트 범위, 아직 확정하지 않은 외부 인터페이스는 [공조 정책 연동 문서](docs/ventilation.md)를 참고하세요.


## ITS 및 에어코리아 API 시연

앱의 **공사·주변 대기 관측** 카드에서 **실제 API에서 조회**를 누르면 서부간선도로 시연 경로와 일치하는 ITS 공사 기록과 가까운 에어코리아 측정소의 최신 초미세먼지 등급을 표시합니다. **실제 GPS·환경으로 주행 시작**은 환경 정보를 새로 조회한 뒤 FusedLocation의 현재 위치와 진행 방향을 연결합니다. 활성 공사 접근과 초미세먼지 나쁨 이상을 각각 판단합니다. 두 조건이 해소되면 앱이 변경한 상태를 운전자 설정으로 복원합니다. 실제 GPS 주행 중 조회는 5분마다 갱신됩니다. 샘플 메뉴의 **샘플로 주행 시작**은 별도 가상 시연 경로입니다.

클론 후 프로젝트 루트에 local.properties가 없다면 [설정 예시](local.properties.example)를 복사합니다. Windows PowerShell에서는 프로젝트 폴더에서 아래 명령을 실행합니다.
```powershell
if (-not (Test-Path .\local.properties)) { Copy-Item .\local.properties.example .\local.properties }
```

Android Studio가 이미 local.properties를 만들었다면 기존 SDK 경로를 보존하고, 파일에 ITS_API_KEY와 AIRKOREA_API_KEY만 추가하세요. 빌드에서 Android SDK를 찾지 못하면 Android Studio의 SDK Manager에서 설치 경로를 확인하고 local.properties의 sdk.dir 또는 ANDROID_HOME을 설정하세요.

샘플 주행은 두 키를 비워둔 상태로 실행할 수 있습니다. 실제 API 조회에는 발급받은 두 키를 로컬 파일에 입력한 뒤 앱을 다시 빌드합니다. local.properties는 Git 추적 대상에서 제외됩니다.


API 키는 소스 저장소에 포함하지 마세요. 현재 빌드는 로컬 키를 `BuildConfig`를 통해 APK에 포함하므로 APK에서도 추출될 수 있습니다. 공개 배포 전에는 서버 중계 또는 제공기관의 키 제한을 적용해야 합니다.
