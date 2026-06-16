package com.dosius.smart.domain.engine.physiology

import javax.inject.Inject

class GlucosePredictor @Inject constructor() {

    fun eventualBG(bgNow: Int, iob: Float, cob: Float, parameters: TherapyParameters, timeSlot: Int): Float {
        val isf = parameters.isfAt(timeSlot)
        val icr = parameters.icrAt(timeSlot)
        return  bgNow - (iob *  isf ) + (cob * isf / icr)
    }

    fun predictDelta5min(iob: Float, cob: Float, parameters: TherapyParameters, timeSlot: Int): Float {
        val isf = parameters.isfAt(timeSlot)
        val icr = parameters.icrAt(timeSlot)
        val ticksInDia = parameters.diaMinutes / 5f
        val insulinEffect = -(iob * isf) / ticksInDia
        val carbEffect = (cob * isf / icr) / ticksInDia
        return insulinEffect + carbEffect
    }

    fun bgi5min(iob: Float, parameters: TherapyParameters, timeSlot: Int): Float {
        val isf = parameters.isfAt(timeSlot)
        val ticksInDia = parameters.diaMinutes / 5f
        return -(iob * isf) / ticksInDia
    }

}