superclean:
	find . -type f -name '*.iml' -exec rm -f -r -v {} +
	rm -f -r -v .idea mobile/build wear/build


# ADB = adb -s TKQ7N18112000190
# PACKAGE_NAME = com.webonastick.ledwatch
# APK_FILENAME = wear/build/outputs/apk/debug/wear-debug.apk

# install:
# 	$(ADB) uninstall $(PACKAGE_NAME) || true
# 	$(ADB) install $(APK_FILENAME)
# reinstall:
# 	$(ADB) install -r $(APK_FILENAME)
# uninstall:
# 	$(ADB) uninstall $(PACKAGE_NAME)

