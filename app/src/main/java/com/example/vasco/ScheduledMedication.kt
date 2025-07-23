package com.example.vasco.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ScheduledMedication(
    @DocumentId var id: String? = null, // ID do documento no Firestore (para updates/deletes)
    val userId: String = "",            // ID do utilizador a quem pertence este agendamento

    // Detalhes do medicamento original (podes guardar o ID do MedicamentoFB se preferires e fazer lookup)
    val nomeMedicamento: String = "",
    val dosagemMedicamento: String? = null, // Ex: "400mg"
    val formaFarmaceuticaMedicamento: String? = null,
    val imagemUrlMedicamento: String? = null, // Para mostrar na lista de agendamentos

    val quantidadeATomar: String = "", // Ex: "1 comprimido", "10 ml", extraído das instruções
    val scheduledTimestamp: Long = 0L,      // Hora agendada como milissegundos UTC
    var status: String = "",       // "Por tomar", "Tomado", "Ignorado"

    @ServerTimestamp
    val createdAt: Date? = null,             // Quando este agendamento foi criado

    // Campos opcionais para referência de como foi gerado
    val originalInstrucoes: String? = null,
    val originalNotas: String? = null
)