    package com.dosius.smart.domain.engine.physiology

    import com.dosius.smart.domain.engine.physiology.InsulinCurve.basalIobFraction
    import com.dosius.smart.domain.engine.physiology.InsulinCurve.iobFraction
    import com.dosius.smart.domain.model.InsulinType
    import kotlinx.datetime.LocalDateTime
    import kotlinx.datetime.toJavaLocalDateTime
    import java.time.temporal.ChronoUnit
    import javax.inject.Inject

    class IOBCalculator @Inject constructor() {
        private fun elapsedMinutes(from: LocalDateTime, to: LocalDateTime): Float {
            return ChronoUnit.MINUTES.between(
                from.toJavaLocalDateTime(),
                to.toJavaLocalDateTime()
            ).toFloat()
        }
        fun calculate(doses: List<InsulinDose>, now: LocalDateTime, diaMinutes: Float = 300f): IOBResult {
            val contributionByDose = mutableListOf<Float>()
            var totalIob = 0f
            for( dose in doses) {
                val elapsedMinutes = elapsedMinutes(dose.timestamp, now)
                if (elapsedMinutes < 0f) continue
                var doseIOB = 0f
                when ( dose.type) {
                     InsulinType.BOLUS ->  doseIOB = dose.units * iobFraction(
                        elapsedMinutes = elapsedMinutes,
                        dia = diaMinutes
                    )
                    InsulinType.BASAL -> doseIOB = dose.units * basalIobFraction(
                        elapsedMinutes = elapsedMinutes,
                    )
                }
                contributionByDose.add(doseIOB)
                totalIob += doseIOB

            }
            return IOBResult(
                totalIob = totalIob,
                contributionByDose = contributionByDose
            )

        }
    }