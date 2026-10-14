#!/bin/bash
# 업무 테이블 — 정본은 src/main/resources/db/manual/create-main.sql (Testcontainers 도 같은 파일을 쓴다)
# skeleton_auth 는 Flyway 가 관리하므로 여기서 만들지 않는다.
set -euo pipefail
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" skeleton_main < /manual/create-main.sql
