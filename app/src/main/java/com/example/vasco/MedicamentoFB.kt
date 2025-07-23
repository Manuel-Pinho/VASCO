package com.example.vasco.model // Ou outro pacote que prefiras para os teus modelos de dados

// Importante: Adiciona um construtor vazio para o Firestore poder desserializar
data class MedicamentoFB(
    val nome: String = "", // Valor default para o construtor vazio
    val dosagem: String? = null,
    val formaFarmaceutica: String? = null,
    val instrucoes: String? = null,
    val notas: String? = null,
    val imagemUrl: String? = null
    // Adiciona outros campos se os definiste no Firestore
) {
    // O construtor vazio é necessário para o Firestore converter
    // os documentos de volta para objetos MedicamentoFB
    // constructor() : this("", null, null, null, null) // Já está implícito com os valores default
}