SHELL := /bin/bash

GRADLE := ./gradlew
SDK_DIR := $(or $(ANDROID_SDK_ROOT),$(ANDROID_HOME),$(shell sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null))
ADB ?= $(if $(wildcard $(SDK_DIR)/platform-tools/adb),$(SDK_DIR)/platform-tools/adb,adb)
EMULATOR ?= $(if $(wildcard $(SDK_DIR)/emulator/emulator),$(SDK_DIR)/emulator/emulator,emulator)
AVD ?= $(shell $(EMULATOR) -list-avds 2>/dev/null | head -n 1)
SERIAL ?= emulator-5554
ADB_TARGET := -s $(SERIAL)
PHONE ?= $(shell $(ADB) devices 2>/dev/null | awk '$$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }')
ADB_PHONE_TARGET := -s '$(PHONE)'
PACKAGE := com.chmouel.noisetimer
ACTIVITY := $(PACKAGE)/.MainActivity
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

.PHONY: help build release test lint check clean emulator stop install run dev-run

help:
	@printf '%s\n' \
		'make build     Build the debug APK' \
		'make release   Build the release APK' \
		'make test      Run JVM unit tests' \
		'make lint      Run Android Lint' \
		'make check     Run tests, lint, and the debug build' \
		'make emulator  Start the first configured Android emulator (AVD=...)' \
		'make install   Build and install the debug APK (SERIAL=...)' \
		'make run       Start the emulator, install, and launch the app' \
		'make dev-run   Install and launch the debug app on a physical device (PHONE=...)' \
		'make stop      Stop the configured emulator' \
		'make clean     Remove build outputs' \
		'' \
		'Variables: AVD=... SERIAL=... PHONE=...'

build:
	$(GRADLE) assembleDebug

release:
	$(GRADLE) assembleRelease

test:
	$(GRADLE) testDebugUnitTest

lint:
	$(GRADLE) lintDebug

check: test lint build

clean:
	$(GRADLE) clean

emulator:
	@command -v $(EMULATOR) >/dev/null || { printf 'error: emulator is not installed\n' >&2; exit 1; }
	@test -n '$(AVD)' || { printf 'error: no AVD found; set AVD to a configured emulator name\n' >&2; exit 1; }
	@$(EMULATOR) -list-avds | grep -Fxq '$(AVD)' || { printf 'error: AVD not found: $(AVD)\n' >&2; exit 1; }
	@if $(ADB) $(ADB_TARGET) get-state >/dev/null 2>&1; then \
		printf 'An emulator is already connected as %s.\n' '$(SERIAL)'; \
	else \
		printf 'Starting AVD %s.\n' '$(AVD)'; \
		$(EMULATOR) @$(AVD) -no-window >/tmp/noise-timer-emulator.log 2>&1 & \
	fi

stop:
	@$(ADB) $(ADB_TARGET) emu kill 2>/dev/null || true

install: build
	$(ADB) $(ADB_TARGET) install -r '$(DEBUG_APK)'

run: emulator build
	@$(ADB) $(ADB_TARGET) wait-for-device
	@printf 'Waiting for Android to finish booting.\n'
	@until [ "$$($(ADB) $(ADB_TARGET) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 1; done
	$(ADB) $(ADB_TARGET) install -r '$(DEBUG_APK)'
	$(ADB) $(ADB_TARGET) shell am start -n '$(ACTIVITY)'

dev-run: build
	@case '$(PHONE)' in \
		'') printf 'error: no physical device attached; set PHONE to its adb serial\n' >&2; exit 1 ;; \
		emulator-*) printf 'error: PHONE=%s is an emulator; dev-run targets a physical device\n' '$(PHONE)' >&2; exit 1 ;; \
	esac
	$(ADB) $(ADB_PHONE_TARGET) install -r '$(DEBUG_APK)'
	$(ADB) $(ADB_PHONE_TARGET) shell am start -n '$(ACTIVITY)'
