package org.basadrito.app.data.moodle

enum class MoodleLoginStep { TOKEN, SITE_INFO }

class MoodleLoginFailure(val step: MoodleLoginStep, cause: Throwable) :
    IllegalStateException("No se pudo completar el acceso de Moodle en ${step.name}.", cause)
