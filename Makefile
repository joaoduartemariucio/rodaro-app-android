ifeq ($(OS),Windows_NT)
GRADLE := gradlew.bat --console=plain
PYTHON ?= python
else
GRADLE := ./gradlew --console=plain
PYTHON ?= python3
endif
COVERAGE_REPORT := app/build/reports/coverage/test/debug/report.xml
BASE ?= origin/main
MIN_COVERAGE ?= 80
ADB ?= adb
LOG_TAGS := Rodaro.Http:D Rodaro.Analytics:D Rodaro.Reminders:D Rodaro.Startup:D AndroidRuntime:E

.PHONY: ci lint format format-check build test coverage scripts-test hosting-deploy clean logs

ci: lint format-check scripts-test build test coverage

lint:
	$(PYTHON) scripts/layer_rules.py
	$(GRADLE) :app:lintDebug

format:
	$(GRADLE) spotlessApply

format-check:
	$(GRADLE) spotlessCheck

build:
	$(GRADLE) :app:assembleDebug :app:assembleDebugUnitTest

test:
	$(GRADLE) :app:testDebugUnitTest :app:createDebugUnitTestCoverageReport

coverage:
	$(PYTHON) scripts/diff_coverage.py --report $(COVERAGE_REPORT) --base $(BASE) --min $(MIN_COVERAGE)

scripts-test:
	$(PYTHON) -m unittest discover -s scripts -p 'test_*.py'

hosting-deploy:
	$(PYTHON) scripts/anp_prices.py --existing https://rodaro.web.app/anp/precos-uf.json --output hosting/public/anp/precos-uf.json
	firebase deploy --only hosting

clean:
	$(GRADLE) clean

logs:
	$(ADB) logcat -v color -v time -s $(LOG_TAGS)
