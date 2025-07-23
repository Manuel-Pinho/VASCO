package com.example.vasco

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vasco.adapter.MedicamentoSearchAdapter
import com.example.vasco.model.MedicamentoFB
import com.example.vasco.util.ScheduleParser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FavouritesActivity : ToolbarActivity() {
    private lateinit var rvFavourites: RecyclerView
    private lateinit var tvNoFavourites: TextView
    private lateinit var favouritesAdapter: MedicamentoSearchAdapter
    private lateinit var dbFirestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var userId: String = ""
    private var favouritesList: MutableList<MedicamentoFB> = mutableListOf()
    private var favouriteNames: MutableSet<String> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favourites)
        setupToolbarClicks()
        rvFavourites = findViewById(R.id.rvFavourites)
        tvNoFavourites = findViewById(R.id.tvNoFavourites)
        rvFavourites.layoutManager = LinearLayoutManager(this)
        dbFirestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid ?: ""
        favouritesAdapter = MedicamentoSearchAdapter(
            emptyList(),
            onAddClick = { medicamentoFB -> addToSchedule(medicamentoFB) },
            favouriteIds = favouriteNames,
            onFavouriteToggle = { medicamentoFB, shouldBeFavourite -> toggleFavourite(medicamentoFB, shouldBeFavourite) }
        )
        rvFavourites.adapter = favouritesAdapter
        loadFavourites()
    }

    private fun loadFavourites() {
        if (userId.isEmpty()) return
        dbFirestore.collection("user_favorites").document(userId).collection("medicamentos")
            .get()
            .addOnSuccessListener { snapshot ->
                favouritesList = snapshot.documents.mapNotNull { it.toObject(MedicamentoFB::class.java) }.toMutableList()
                favouriteNames = favouritesList.map { it.nome }.toMutableSet()
                updateUI()
            }
            .addOnFailureListener {
                favouritesList = mutableListOf()
                favouriteNames = mutableSetOf()
                updateUI()
            }
    }

    private fun updateUI() {
        favouritesAdapter.updateData(favouritesList, favouriteNames)
        tvNoFavourites.visibility = if (favouritesList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleFavourite(medicamentoFB: MedicamentoFB, shouldBeFavourite: Boolean) {
        val favDocRef = dbFirestore.collection("user_favorites").document(userId).collection("medicamentos").document(medicamentoFB.nome)
        if (shouldBeFavourite) {
            val favData = hashMapOf(
                "nome" to medicamentoFB.nome,
                "dosagem" to medicamentoFB.dosagem,
                "formaFarmaceutica" to medicamentoFB.formaFarmaceutica,
                "imagemUrl" to medicamentoFB.imagemUrl,
                "instrucoes" to medicamentoFB.instrucoes,
                "notas" to medicamentoFB.notas
            )
            favDocRef.set(favData).addOnSuccessListener {
                favouriteNames.add(medicamentoFB.nome)
                if (favouritesList.none { it.nome == medicamentoFB.nome }) {
                    favouritesList.add(medicamentoFB)
                }
                updateUI()
            }
        } else {
            favDocRef.delete().addOnSuccessListener {
                favouriteNames.remove(medicamentoFB.nome)
                favouritesList.removeAll { it.nome == medicamentoFB.nome }
                updateUI()
            }
        }
    }

    private fun addToSchedule(medicamentoFB: MedicamentoFB) {
        val calHoje = Calendar.getInstance()
        val inicioHoje = (calHoje.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        CoroutineScope(Dispatchers.Main).launch {
            val agendamentosPorTomar = dbFirestore.collection("user_medication_schedules")
                .whereEqualTo("userId", userId)
                .whereEqualTo("nomeMedicamento", medicamentoFB.nome)
                .whereEqualTo("status", "Por tomar")
                .whereGreaterThanOrEqualTo("scheduledTimestamp", inicioHoje)
                .limit(1)
                .get()
                .await()
            if (!agendamentosPorTomar.isEmpty) {
                Toast.makeText(this@FavouritesActivity, "${medicamentoFB.nome} já está agendado e 'Por tomar'.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val novosAgendamentos = ScheduleParser.parsearInstrucoesESimularHorarios(medicamentoFB, userId, Calendar.getInstance().time)
            if (novosAgendamentos.isEmpty()) {
                val dataReferenciaFormatada = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                Toast.makeText(
                    this@FavouritesActivity,
                    "Não foram gerados novos horários para ${medicamentoFB.nome} a partir de ${dataReferenciaFormatada}.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                var agendamentosSalvosComSucesso = 0
                var falhasAoSalvar = 0
                val totalAgendamentosParaProcessar = novosAgendamentos.size
                novosAgendamentos.forEach { agendamentoParaSalvar ->
                    dbFirestore.collection("user_medication_schedules")
                        .add(agendamentoParaSalvar)
                        .addOnSuccessListener {
                            agendamentosSalvosComSucesso++
                            if (agendamentosSalvosComSucesso + falhasAoSalvar == totalAgendamentosParaProcessar) {
                                if (falhasAoSalvar == 0) {
                                    Toast.makeText(this@FavouritesActivity, "${medicamentoFB.nome} adicionado ao seu horário!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@FavouritesActivity, "Adicionado ${medicamentoFB.nome} com ${falhasAoSalvar} erro(s) no agendamento.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            falhasAoSalvar++
                            if (agendamentosSalvosComSucesso + falhasAoSalvar == totalAgendamentosParaProcessar) {
                                Toast.makeText(this@FavouritesActivity, "Erro ao adicionar ${medicamentoFB.nome}. ${falhasAoSalvar} falha(s).", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
        }
    }
} 