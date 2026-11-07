# Consumer ProGuard rules for the :data module.
# Applied to any module that depends on :data (currently :app).
#
# Empty on purpose: NewPipeExtractor, Media3 and Room all ship their own
# consumer rules. Add project-specific keep rules here only when a class
# inside :data is accessed reflectively and keeps breaking in release builds.