package com.example.vasco.util

import com.example.vasco.model.MedicamentoFB
import com.example.vasco.model.ScheduledMedication
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.util.Log // Para depuração

object ScheduleParser {

    private const val TAG = "ScheduleParser"

    // Horas aproximadas para refeições (podes ajustar)
    private const val HORA_PEQUENO_ALMOCO = 8
    private const val HORA_ALMOCO = 13
    private const val HORA_JANTAR = 20
    private const val HORA_DEITAR = 22 // Se for "ao deitar"

    fun parsearInstrucoesESimularHorarios(
        medicamento: MedicamentoFB,
        userId: String,
        dataReferenciaAtual: Date = Date() // Dia para o qual estamos a tentar agendar
    ): List<ScheduledMedication> {
        val agendamentos = mutableListOf<ScheduledMedication>()
        val instrucoes = medicamento.instrucoes?.lowercase(Locale.getDefault()) ?: ""
        val notas = medicamento.notas?.lowercase(Locale.getDefault()) ?: ""
        val textoCombinado = "$instrucoes $notas".trim()

        Log.d(TAG, "Parsing para: ${medicamento.nome}, Instruções: '$instrucoes', Notas: '$notas'")

        var quantidadeATomar = extrairQuantidade(medicamento, textoCombinado)
        val (numDiasToma, frequenciaDiaria) = extrairDuracaoEFrequencia(textoCombinado)

        Log.d(TAG, "Extraído: Quantidade='$quantidadeATomar', DiasToma=$numDiasToma, FrequenciaDiaria=$frequenciaDiaria")

        val calInicio = Calendar.getInstance()
        calInicio.time = dataReferenciaAtual
        // Ajustar para o início do dia da data de referência para consistência no loop de dias
        calInicio.set(Calendar.HOUR_OF_DAY, 0)
        calInicio.set(Calendar.MINUTE, 0)
        calInicio.set(Calendar.SECOND, 0)
        calInicio.set(Calendar.MILLISECOND, 0)

        Log.d(TAG, "Data de Referência Atual para Parsing: $dataReferenciaAtual")

        for (diaOffset in 0 until numDiasToma) {
            val calDiaAtual = calInicio.clone() as Calendar
            calDiaAtual.add(Calendar.DAY_OF_YEAR, diaOffset)
            Log.d(TAG, "Processando diaOffset: $diaOffset, Data do Dia Atual do Loop: ${calDiaAtual.time}")

            val horariosDoDia = mutableListOf<Int>() // Horas do dia (0-23)

            when (frequenciaDiaria) {
                1 -> { // 1 vez ao dia
                    if (textoCombinado.contains("manhã") || textoCombinado.contains("pequeno almoço") || textoCombinado.contains("pequeno-almoço")) {
                        horariosDoDia.add(HORA_PEQUENO_ALMOCO)
                    } else if (textoCombinado.contains("almoço")) {
                        horariosDoDia.add(HORA_ALMOCO)
                    } else if (textoCombinado.contains("jantar") || textoCombinado.contains("noite") || textoCombinado.contains("deitar")) {
                        horariosDoDia.add(HORA_JANTAR) // Ou HORA_DEITAR se for "ao deitar"
                    } else {
                        // Default para 1 vez ao dia (se não especificado)
                        val calAgora = Calendar.getInstance()
                        calAgora.time = dataReferenciaAtual // Usa a hora atual da data de referência
                        if (calAgora.get(Calendar.HOUR_OF_DAY) < HORA_ALMOCO -1) { // Se antes do almoço
                            horariosDoDia.add(HORA_ALMOCO)
                        } else if (calAgora.get(Calendar.HOUR_OF_DAY) < HORA_JANTAR -1) { // Se antes do jantar
                            horariosDoDia.add(HORA_JANTAR)
                        } else { // Default para o dia seguinte de manhã se já passou tudo
                            if (diaOffset == 0) { // Só adiciona para dia seguinte se estivermos no primeiro dia de parsing
                                val proximoDiaCal = calDiaAtual.clone() as Calendar
                                proximoDiaCal.add(Calendar.DAY_OF_YEAR, 1)
                                proximoDiaCal.set(Calendar.HOUR_OF_DAY, HORA_PEQUENO_ALMOCO)
                                if (numDiasToma > 1 || agendamentos.none { isSameDay(it.scheduledTimestamp, proximoDiaCal.timeInMillis) } ) {
                                    // Adiciona apenas se houver mais dias de toma ou se ainda não foi agendado para este dia
                                } // Lógica complexa, por agora, só um default para o dia atual
                                horariosDoDia.add(HORA_ALMOCO) // Simplificando para o almoço do dia atual
                            } else {
                                horariosDoDia.add(HORA_ALMOCO) // Para os outros dias, default almoço
                            }
                        }
                    }
                }
                2 -> { // 2 vezes ao dia
                    horariosDoDia.add(HORA_PEQUENO_ALMOCO) // Ex: Manhã
                    horariosDoDia.add(HORA_JANTAR)       // Ex: Noite
                    // Poderia ser mais específico (ex: almoço e jantar se "após refeições")
                    if (textoCombinado.contains("almoço") && textoCombinado.contains("jantar")) {
                        horariosDoDia.clear()
                        horariosDoDia.add(HORA_ALMOCO)
                        horariosDoDia.add(HORA_JANTAR)
                    }
                }
                3 -> { // 3 vezes ao dia
                    horariosDoDia.add(HORA_PEQUENO_ALMOCO)
                    horariosDoDia.add(HORA_ALMOCO)
                    horariosDoDia.add(HORA_JANTAR)
                }
                // Casos de "de X em X horas"
                else -> if (frequenciaDiaria == 0 && textoCombinado.contains("hora")) { // Pode ser "de X em X horas"
                    val deXEmXHorasMatch = Regex("de\\s*(\\d+)\\s*em\\s*(\\d+)\\s*horas?").find(textoCombinado)
                    if (deXEmXHorasMatch != null) {
                        val horasIntervalo = deXEmXHorasMatch.groupValues[1].toIntOrNull() ?: 8
                        var horaAtual = HORA_PEQUENO_ALMOCO // Começa de manhã
                        while (horaAtual < 24) {
                            horariosDoDia.add(horaAtual)
                            horaAtual += horasIntervalo
                        }
                    } else { // Fallback se "hora" mas sem padrão X em X
                        horariosDoDia.add(HORA_ALMOCO)
                    }
                } else if (frequenciaDiaria > 3) { // Mais de 3 vezes, distribui
                    val intervalo = 24 / frequenciaDiaria
                    for(i in 0 until frequenciaDiaria) horariosDoDia.add(HORA_PEQUENO_ALMOCO + i * intervalo)
                }
                else { // Fallback geral
                    horariosDoDia.add(HORA_ALMOCO) // Default: uma vez ao almoço
                }
            }
            Log.d(TAG, "Horários calculados para ${calDiaAtual.time}: $horariosDoDia")

            for (hora in horariosDoDia) {
                val calAgendamento = calDiaAtual.clone() as Calendar
                calAgendamento.set(Calendar.HOUR_OF_DAY, hora)
                calAgendamento.set(Calendar.MINUTE, 0) // Horas "redondas"

                Log.d(TAG, "Tentando agendar para: ${calAgendamento.time} (Timestamp: ${calAgendamento.timeInMillis})")
                Log.d(TAG, "Comparando com dataReferenciaAtual: ${dataReferenciaAtual} (Timestamp: ${dataReferenciaAtual.time})")
                Log.d(TAG, "isSameDay(calAgendamento, dataReferenciaAtual): ${isSameDay(calAgendamento.time, dataReferenciaAtual)}")

                // Não agenda para o passado em relação à data de referência atual (data em que o utilizador está a adicionar)
                if (calAgendamento.timeInMillis < dataReferenciaAtual.time && isSameDay(calAgendamento.time, dataReferenciaAtual)) {
                    Log.d(TAG, "Ignorando horário passado para hoje: ${calAgendamento.time}")
                    continue
                }

                agendamentos.add(
                    ScheduledMedication(
                        userId = userId,
                        nomeMedicamento = medicamento.nome,
                        dosagemMedicamento = medicamento.dosagem,
                        formaFarmaceuticaMedicamento = medicamento.formaFarmaceutica,
                        imagemUrlMedicamento = medicamento.imagemUrl,
                        quantidadeATomar = quantidadeATomar,
                        scheduledTimestamp = calAgendamento.timeInMillis,
                        status = "Por tomar",
                        originalInstrucoes = medicamento.instrucoes,
                        originalNotas = medicamento.notas
                    )
                )
                Log.d(TAG, "Agendado: ${medicamento.nome} para ${calAgendamento.time}")
            }
        }
        // Remove duplicados exatos de timestamp (pouco provável com esta lógica, mas seguro)
        Log.d(TAG, "Total de agendamentos gerados: ${agendamentos.size}")
        return agendamentos.distinctBy { it.scheduledTimestamp }
    }

    private fun extrairQuantidade(medicamento: MedicamentoFB, texto: String): String {
        // Tenta ser um pouco mais específico com a unidade se possível
        val unidadeBase = medicamento.formaFarmaceutica?.lowercase(Locale.getDefault())?.let {
            when {
                it.contains("comprimido") -> "comprimido"
                it.contains("cápsula") -> "cápsula"
                it.contains("xarope") || it.contains("suspensão") -> "ml" // Assumir ml para líquidos
                it.contains("gota") -> "gota"
                else -> "dose"
            }
        } ?: "dose"

        val regex = Regex("(\\d+)\\s*(${unidadeBase}|comprimido|cápsula|ml|gota|dose)s?")
        regex.find(texto)?.let {
            val num = it.groupValues[1]
            val unit = it.groupValues[2]
            var finalUnit = unit
            if ((num.toIntOrNull() ?: 1) > 1 && !unit.endsWith("s")) {
                finalUnit = when(unit) { // Plural simples
                    "ml", "gota", "dose" -> unit // Já são formas que podem ser usadas no plural
                    else -> unit + "s"
                }
            }
            return "$num $finalUnit"
        }
        return "1 $unidadeBase" // Default
    }

    private fun extrairDuracaoEFrequencia(texto: String): Pair<Int, Int> {
        var numDiasToma = 1 // Default
        var frequenciaDiaria = 0 // 0 significa que não foi explicitamente encontrado "X vezes ao dia"

        // Duração: "por X dias", "durante X dias"
        val duracaoMatch = Regex("(?:por|durante)\\s*(\\d+)\\s*dias?").find(texto)
        duracaoMatch?.let {
            numDiasToma = it.groupValues[1].toIntOrNull() ?: 1
            if (numDiasToma == 0) numDiasToma = 1 // Evitar 0 dias
        }
        Log.d(TAG, "Duração encontrada: $numDiasToma dias")

        // Frequência: "X vez(es) por/ao dia"
        val frequenciaMatch = Regex("(\\d+)\\s*vez(?:es)?\\s*(?:por|ao)\\s*dia").find(texto)
        frequenciaMatch?.let {
            frequenciaDiaria = it.groupValues[1].toIntOrNull() ?: 0
        }
        Log.d(TAG, "Frequência encontrada: $frequenciaDiaria vezes/dia")

        // Se não encontrou "X vezes ao dia", mas encontrou "de X em X horas", calcula uma frequência aproximada
        if (frequenciaDiaria == 0 && texto.contains("hora")) {
            val deXEmXHorasMatch = Regex("de\\s*(\\d+)\\s*em\\s*(\\d+)\\s*horas?").find(texto)
            deXEmXHorasMatch?.let {
                val horasIntervalo = it.groupValues[1].toIntOrNull()
                if (horasIntervalo != null && horasIntervalo > 0) {
                    frequenciaDiaria = (24 / horasIntervalo) // Frequência aproximada
                    Log.d(TAG, "Frequência calculada por X em X horas: $frequenciaDiaria")
                }
            }
        }
        // Se ainda não há frequência, mas o texto indica alguma toma diária (ex: manhã, noite)
        if (frequenciaDiaria == 0 && (texto.contains("manhã") || texto.contains("almoço") || texto.contains("jantar") || texto.contains("noite") || texto.contains("deitar"))){
            frequenciaDiaria = 1 // Assumir pelo menos uma vez se houver menção a período do dia
            if((texto.contains("manhã") || texto.contains("pequeno almoço")) && (texto.contains("noite") || texto.contains("jantar"))) frequenciaDiaria = 2
            // Adicionar mais lógica se necessário para 3 vezes, etc.
            Log.d(TAG, "Frequência inferida por menção a período do dia: $frequenciaDiaria")
        }


        // Se nenhuma frequência foi encontrada, assume 1 por defeito
        if (frequenciaDiaria == 0) {
            frequenciaDiaria = 1
            Log.d(TAG, "Nenhuma frequência explícita, default para $frequenciaDiaria vez/dia")
        }


        return Pair(numDiasToma, frequenciaDiaria)
    }

    private fun isSameDay(timeInMillis1: Long, timeInMillis2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timeInMillis1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timeInMillis2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    private fun isSameDay(date1: Date, date2: Date): Boolean { // Sobrecarga para Date
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
