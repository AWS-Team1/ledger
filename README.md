# ledger

Bank of Anthos의 **원장 도메인** 소스 저장소입니다. `ledger-db`를 소유하는 서비스만 담습니다.

## 구성

| 경로 | 역할 |
| --- | --- |
| `src/ledger/ledgerwriter` | 거래 기록. 원장에 쓰는 유일한 서비스입니다 |
| `src/ledger/balancereader` | 잔액 조회 캐시 |
| `src/ledger/transactionhistory` | 거래 이력 조회 캐시 |
| `src/ledger/ledger-db` | 원장 스키마. 아래 “DB 디렉터리” 참조 |
| `src/ledgermonolith` | 분리 전 모놀리스 소스. Dockerfile이 없고 배포 대상이 아닙니다 |

Java 전용입니다. `pyproject.toml` 이 없습니다 — Python 서비스는 `accounts`, `frontend` 저장소에
있습니다.

## 왜 이 넷인가

`ledger-db` 라는 **하나의 데이터 소유권 경계**입니다. 계정 도메인(`accounts-db`)을 다루는
서비스는 `accounts` 저장소에 있고, 두 도메인은 DB를 공유하지 않습니다.

읽기 두 서비스는 `ledgerwriter` 가 쓴 내용을 폴링해 자기 캐시를 갱신합니다. 그래서 셋의
릴리스가 서로 얽히며, 한 저장소에 두는 편이 맞습니다.

## 루트 `pom.xml` 은 애그리게이터입니다

각 서비스의 `<parent>` 는 루트 pom이 아니라 Maven Central의
`spring-boot-starter-parent:3.5.15` 입니다. Maven에서 집합(`<modules>`)은 상속이 아니므로
루트의 `pluginManagement` 는 자식에게 전달되지 않으며, 실제로 각 서비스가 jib·jacoco 버전을
스스로 명시합니다. **각 서비스는 독립적으로 빌드됩니다.**

루트 pom이 제공하는 것은 리액터 편의 두 가지입니다.

```sh
./mvnw test                                   # 4개 모듈 한 번에
./mvnw -pl src/ledger/balancereader test      # 단일 서비스
./mvnw -pl src/ledger/balancereader test -Dtest=BalanceReaderControllerTest
```

`<modules>` 가 `src/ledger/…` 경로를 가리키므로 **디렉터리 구조를 바꾸면 pom도 고쳐야 합니다.**
서비스를 저장소 루트로 끌어올리지 마십시오.

테스트가 있는 모듈은 `balancereader`, `ledgerwriter`, `transactionhistory` 셋입니다.
`ledgermonolith` 에는 테스트가 없습니다.

## 이미지 빌드

각 서비스의 `Dockerfile` 은 **자기 디렉터리만을 컨텍스트로** 하는 멀티스테이지 빌드이며,
컨테이너 안에서 `mvn package` 를 직접 실행합니다. 루트 pom도, `mvnw` 도 사용하지 않습니다.

```sh
docker build -t bank-ledgerwriter       ./src/ledger/ledgerwriter
docker build -t bank-balancereader      ./src/ledger/balancereader
docker build -t bank-transactionhistory ./src/ledger/transactionhistory
```

`pom.xml` 에 jib 플러그인 설정도 함께 들어 있어 Maven으로 이미지를 만드는 경로도 열려 있습니다.
둘 중 무엇을 CI의 정본으로 삼을지는 정해지지 않았습니다.

CI에서는 JDK 설치가 필요합니다. `mvnw` 는 `JAVA_HOME` 이 없으면 경고를 내고 PATH의 `java` 를
찾으므로, 러너에 JDK 17을 명시적으로 설치하십시오.

## 실행 환경

| 변수 | 용도 |
| --- | --- |
| `SPRING_DATASOURCE_URL` `SPRING_DATASOURCE_USERNAME` `SPRING_DATASOURCE_PASSWORD` | DB 접속 |
| `DB_HOST` `DB_PORT` `POSTGRES_USER` `POSTGRES_PASSWORD` `LEDGER_DB_NAME` | DB 접속 구성 요소 |
| `PUB_KEY_PATH` | JWT 검증용 RS256 **공개키**. 이 저장소의 서비스는 개인키를 갖지 않습니다 |
| `LOCAL_ROUTING_NUM` `PORT` `VERSION` | 동작 설정 |
| `POLL_MS` `CACHE_SIZE` | 캐시 갱신 주기·크기 (balancereader, transactionhistory) |
| `HISTORY_LIMIT` | 이력 조회 상한 (transactionhistory) |
| `BALANCES_API_ADDR` | 잔액 조회 주소 (ledgerwriter) |

주입되는 값의 정본은 `gitops` 저장소의 `k8s/base/transaction/` 매니페스트입니다.
실제 인증정보는 이 저장소에 두지 않습니다.

## DB 디렉터리 — AWS 배포 경로에서는 쓰이지 않습니다

`src/ledger/ledger-db/Dockerfile` 은 `postgres:16.14-alpine` 에 `initdb/` 를 얹어
**DB 컨테이너 이미지**를 만듭니다. 그러나 AWS 배포는 RDS를 사용하며, `gitops` 의 `k8s/base/` 에
DB 워크로드가 없고 배포 대상 이미지 목록에도 DB 이미지가 없습니다.

스키마는 `gitops` 의 `k8s/components/aws/db-bootstrap/` Job이 적용합니다. 그 Job이 쓰는
`sql/0_init_tables.sql` 은 이 저장소의 `ledger-db/initdb/0_init_tables.sql` 과 **내용이 같습니다.**
스키마를 바꿀 때는 **두 곳을 함께 고치거나**, 한쪽을 정본으로 정하고 다른 한쪽을 지워야 합니다.
한쪽만 고치면 조용히 어긋납니다.

## 저장소 범위

애플리케이션 코드와 이미지 빌드 정의만 포함합니다. Terraform, Kubernetes 매니페스트, 배포
스크립트는 포함하지 않습니다 — 각각 `infra`, `gitops` 저장소가 소유합니다.

소스 기준: `dlcodb94/aws-bank-of-anthos`의 `0ca63b4` 커밋. 라이선스는 `LICENSE`를 참고합니다.
