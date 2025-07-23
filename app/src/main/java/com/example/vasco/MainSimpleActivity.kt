package com.example.vasco

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.example.vasco.adapter.MedicamentoSearchAdapter
import com.example.vasco.model.MedicamentoFB
import com.example.vasco.model.ScheduledMedication
import com.example.vasco.util.ScheduleParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.vasco.adapter.NextMedicationsAdapter
import java.util.concurrent.TimeUnit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.DialogInterface
import android.widget.ImageButton
import com.google.firebase.firestore.ListenerRegistration

class MainSimpleActivity : ToolbarActivity() {

    companion object {
        private const val PREFS_NAME_NOTIF  = "vasco_prefs"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val CHANNEL_ID       = "main_activity_channel"
        private const val CHANNEL_NAME     = "Canal MainSimple"
        private const val NOTIFICATION_ID  = 2001
    }

    private lateinit var fabAddMedicamento: FloatingActionButton
    private lateinit var searchResultsAdapter: MedicamentoSearchAdapter
    private var searchDialog: AlertDialog? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var dbFirestore: FirebaseFirestore
    private val calendar = Calendar.getInstance()
    private lateinit var rvNextMeds: RecyclerView
    private lateinit var nextMedsAdapter: NextMedicationsAdapter
    private lateinit var tvNoMedsInfo: TextView
    private lateinit var timerText: TextView
    private lateinit var labelTimer: TextView
    private var warningMinutes: Int = 30 // Default, will be loaded from prefs
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var medsToday: List<ScheduledMedication> = emptyList()
    private var nextMeds: List<ScheduledMedication> = emptyList()
    private var nextMedTime: Long? = null
    private var currentUiState: UiState = UiState.NORMAL
    private lateinit var btnPrevMed: ImageButton
    private lateinit var btnNextMed: ImageButton
    private var userFavouriteNames: MutableSet<String> = mutableSetOf()
    private var favouritesListener: ListenerRegistration? = null

    enum class UiState { NORMAL, WARNING, TAKE_NOW }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_simple)

        // AVISO SOBRE ALARMES EXATOS (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "Para receber notificações na hora certa, ative a permissão de alarmes exatos nas definições do sistema.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        setupToolbarClicks()
        auth = FirebaseAuth.getInstance()
        dbFirestore = Firebase.firestore
        fabAddMedicamento = findViewById(R.id.fabAddMedicamento)
        fabAddMedicamento.setOnClickListener { showSearchMedicamentoDialog() }
        timerText = findViewById(R.id.timerText)
        labelTimer = findViewById(R.id.labelTimer)
        tvNoMedsInfo = findViewById(R.id.tvNoMedsInfo)
        rvNextMeds = findViewById(R.id.rvNextMeds)
        rvNextMeds.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        nextMedsAdapter = NextMedicationsAdapter(emptyList()) { scheduledMed ->
            showScheduledMedDetailsDialog(scheduledMed)
        }
        rvNextMeds.adapter = nextMedsAdapter
        warningMinutes = getWarningMinutesFromPrefs()
        checkPermissionsAndSchedule()
        createNotificationChannel()
        triggerNotification()
        findViewById<ImageView>(R.id.robotImage).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        loadTodaysMedications()
        btnPrevMed = findViewById(R.id.btnPrevMed)
        btnNextMed = findViewById(R.id.btnNextMed)
        btnPrevMed.setOnClickListener { scrollToPreviousMed() }
        btnNextMed.setOnClickListener { scrollToNextMed() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notificações ao entrar na MainSimpleActivity" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun triggerNotification() {
        val prefs = getSharedPreferences(PREFS_NAME_NOTIF, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_NOTIFICATIONS, true)) return

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Olá, seja bem-vindo!")
            .setContentText("Você entrou na MainSimpleActivity.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun checkPermissionsAndSchedule() {
        // Runtime permission para notificações (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            return
        }
        // Exact alarms (Android 12+)
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .apply { data = Uri.parse("package:$packageName") }
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001
            && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            checkPermissionsAndSchedule()
        }
    }

    private fun showSearchMedicamentoDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_medicamento, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchMedicamentoDialog)
        val rvResults = dialogView.findViewById<RecyclerView>(R.id.rvSearchResultsDialog)

        rvResults.layoutManager = LinearLayoutManager(this)
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Precisa estar logado para adicionar medicamentos.", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = currentUser.uid

        // Load favourites from Firestore
        loadUserFavourites(userId) { favouritesSet ->
            userFavouriteNames = favouritesSet.toMutableSet()
            searchResultsAdapter = MedicamentoSearchAdapter(
                emptyList(),
                onAddClick = { medicamentoFB ->
                    lifecycleScope.launch {
                        val calHoje = Calendar.getInstance()
                        val inicioHoje = (calHoje.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val agendamentosPorTomar = dbFirestore.collection("user_medication_schedules")
                            .whereEqualTo("userId", userId)
                            .whereEqualTo("nomeMedicamento", medicamentoFB.nome)
                            .whereEqualTo("status", "Por tomar")
                            .whereGreaterThanOrEqualTo("scheduledTimestamp", inicioHoje)
                            .limit(1)
                            .get()
                            .await()
                        if (!agendamentosPorTomar.isEmpty) {
                            Toast.makeText(this@MainSimpleActivity, "${medicamentoFB.nome} já está agendado e 'Por tomar'.", Toast.LENGTH_LONG).show()
                            searchDialog?.dismiss()
                            return@launch
                        }
                        val novosAgendamentos = ScheduleParser.parsearInstrucoesESimularHorarios(medicamentoFB, userId, calendar.time)
                        if (novosAgendamentos.isEmpty()) {
                            val dataReferenciaFormatada = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)
                            Toast.makeText(
                                this@MainSimpleActivity,
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
                                        // Só agenda notificação se o utilizador tiver ativado nas definições
                                        val prefsNotif = getSharedPreferences("vasco_prefs", Context.MODE_PRIVATE)
                                        if (prefsNotif.getBoolean("notifications_enabled", false)) {
                                            scheduleMedicationAlarm(
                                                this@MainSimpleActivity,
                                                agendamentoParaSalvar.nomeMedicamento,
                                                agendamentoParaSalvar.scheduledTimestamp
                                            )
                                        }
                                        if (agendamentosSalvosComSucesso + falhasAoSalvar == totalAgendamentosParaProcessar) {
                                            if (falhasAoSalvar == 0) {
                                                Toast.makeText(this@MainSimpleActivity, "${medicamentoFB.nome} adicionado ao seu horário!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@MainSimpleActivity, "Adicionado ${medicamentoFB.nome} com ${falhasAoSalvar} erro(s) no agendamento.", Toast.LENGTH_LONG).show()
                                            }
                                            searchDialog?.dismiss()
                                            loadTodaysMedications()
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        falhasAoSalvar++
                                        Log.e("ADD_SCHEDULE", "Erro ao salvar agendamento para ${medicamentoFB.nome}", e)
                                        if (agendamentosSalvosComSucesso + falhasAoSalvar == totalAgendamentosParaProcessar) {
                                            Toast.makeText(this@MainSimpleActivity, "Erro ao adicionar ${medicamentoFB.nome}. ${falhasAoSalvar} falha(s).", Toast.LENGTH_LONG).show()
                                            searchDialog?.dismiss()
                                        }
                                    }
                            }
                        }
                    }
                },
                favouriteIds = userFavouriteNames,
                onFavouriteToggle = { medicamentoFB, shouldBeFavourite ->
                    toggleFavourite(userId, medicamentoFB, shouldBeFavourite)
                }
            )
            rvResults.adapter = searchResultsAdapter
            // Initial empty search
            searchResultsAdapter.updateData(emptyList(), userFavouriteNames)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    pesquisarMedicamentosNoFirestore(query)
                } else {
                    searchResultsAdapter.updateData(emptyList(), userFavouriteNames)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        searchDialog?.show()
    }

    private fun loadUserFavourites(userId: String, onLoaded: (Set<String>) -> Unit) {
        dbFirestore.collection("user_favorites").document(userId).collection("medicamentos")
            .get()
            .addOnSuccessListener { snapshot ->
                val favs = snapshot.documents.mapNotNull { it.getString("nome") }.toSet()
                onLoaded(favs)
            }
            .addOnFailureListener {
                onLoaded(emptySet())
            }
    }

    private fun toggleFavourite(userId: String, medicamentoFB: MedicamentoFB, shouldBeFavourite: Boolean) {
        val favDocRef = dbFirestore.collection("user_favorites").document(userId).collection("medicamentos").document(medicamentoFB.nome)
        if (shouldBeFavourite) {
            // Add to favourites
            val favData = hashMapOf(
                "nome" to medicamentoFB.nome,
                "dosagem" to medicamentoFB.dosagem,
                "formaFarmaceutica" to medicamentoFB.formaFarmaceutica,
                "imagemUrl" to medicamentoFB.imagemUrl,
                "instrucoes" to medicamentoFB.instrucoes,
                "notas" to medicamentoFB.notas
            )
            favDocRef.set(favData).addOnSuccessListener {
                userFavouriteNames.add(medicamentoFB.nome)
                searchResultsAdapter.updateData(searchResultsAdapter.medicamentos, userFavouriteNames)
            }
        } else {
            // Remove from favourites
            favDocRef.delete().addOnSuccessListener {
                userFavouriteNames.remove(medicamentoFB.nome)
                searchResultsAdapter.updateData(searchResultsAdapter.medicamentos, userFavouriteNames)
            }
        }
    }

    private fun pesquisarMedicamentosNoFirestore(query: String) {
        if (query.isEmpty()) {
            searchResultsAdapter.updateData(emptyList(), userFavouriteNames)
            return
        }
        Log.d("FIRESTORE_SEARCH", "Pesquisando por: $query")

        dbFirestore.collection("medicamentos")
            .orderBy("nome")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val resultados = mutableListOf<MedicamentoFB>()
                val queryLower = query.lowercase()
                Log.d("FIRESTORE_SEARCH", "Sucesso! Documentos encontrados: ${querySnapshot?.size() ?: 0}")

                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    for (document in querySnapshot.documents) {
                        Log.d("FIRESTORE_SEARCH", "Documento: ${document.id} => ${document.data}")
                        val medicamento = document.toObject(MedicamentoFB::class.java)
                        if (medicamento != null) {
                            Log.d("FIRESTORE_SEARCH", "Medicamento convertido: ${medicamento.nome}, Query: $queryLower, Nome Lower: ${medicamento.nome.lowercase()}")
                            if (medicamento.nome.lowercase().contains(queryLower)) {
                                resultados.add(medicamento)
                                Log.d("FIRESTORE_SEARCH", "Adicionado aos resultados: ${medicamento.nome}")
                            }
                        } else {
                            Log.w("FIRESTORE_SEARCH", "Falha ao converter documento ${document.id} para MedicamentoFB")
                        }
                    }
                }
                Log.d("FIRESTORE_SEARCH", "Total de resultados filtrados: ${resultados.size}")
                searchResultsAdapter.updateData(resultados, userFavouriteNames)
            }
            .addOnFailureListener { exception ->
                Log.e("FIRESTORE_SEARCH", "Erro ao pesquisar medicamentos.", exception)
                searchResultsAdapter.updateData(emptyList(), userFavouriteNames)
            }
    }

    private fun getWarningMinutesFromPrefs(): Int {
        val prefs = getSharedPreferences("vasco_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("warning_minutes", 30).coerceIn(5, 60)
    }
    private fun setWarningMinutesToPrefs(minutes: Int) {
        val prefs = getSharedPreferences("vasco_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("warning_minutes", minutes.coerceIn(5, 60)).apply()
        warningMinutes = minutes.coerceIn(5, 60)
    }

    private fun loadTodaysMedications() {
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis
        dbFirestore.collection("user_medication_schedules")
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("scheduledTimestamp", startOfDay)
            .whereLessThanOrEqualTo("scheduledTimestamp", endOfDay)
            .orderBy("scheduledTimestamp")
            .get()
            .addOnSuccessListener { documents ->
                val meds = mutableListOf<ScheduledMedication>()
                for (doc in documents) {
                    val med = doc.toObject(ScheduledMedication::class.java)
                    if (med.status.equals("Por tomar", ignoreCase = true)) {
                        meds.add(med)
                    }
                }
                medsToday = meds
                updateNextMedicationUI()
            }
            .addOnFailureListener {
                medsToday = emptyList()
                updateNextMedicationUI()
            }
    }

    private fun updateNextMedicationUI() {
        if (countdownRunnable != null) handler.removeCallbacks(countdownRunnable!!)
        if (medsToday.isEmpty()) {
            timerText.text = "--:--"
            rvNextMeds.visibility = View.GONE
            tvNoMedsInfo.visibility = View.VISIBLE
            labelTimer.text = "Nenhum medicamento agendado para hoje."
            return
        }
        tvNoMedsInfo.visibility = View.GONE
        // Find the next scheduled time (the earliest in the future)
        val now = System.currentTimeMillis()
        val nextTime = medsToday.minOfOrNull { it.scheduledTimestamp } ?: return
        nextMedTime = nextTime
        nextMeds = medsToday.filter { it.scheduledTimestamp == nextTime }
        rvNextMeds.visibility = View.VISIBLE
        nextMedsAdapter.updateData(nextMeds)
        startCountdown()
    }

    private fun startCountdown() {
        countdownRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val timeLeft = (nextMedTime ?: now) - now
                if (timeLeft <= 0) {
                    setUiState(UiState.TAKE_NOW)
                    timerText.text = "00:00:00"
                } else {
                    val hours = TimeUnit.MILLISECONDS.toHours(timeLeft)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60
                    timerText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    if (timeLeft <= warningMinutes * 60 * 1000) {
                        setUiState(UiState.WARNING)
                    } else {
                        setUiState(UiState.NORMAL)
                    }
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun setUiState(state: UiState) {
        if (currentUiState == state) return
        currentUiState = state
        when (state) {
            UiState.NORMAL -> {
                labelTimer.text = "Próximo medicamento em:"
                // Optionally reset background/colors
            }
            UiState.WARNING -> {
                labelTimer.text = "! Próximo medicamento em:"
                // Optionally set warning color/background
            }
            UiState.TAKE_NOW -> {
                labelTimer.text = "Hora de tomar o medicamento!"
                // Optionally set take-now color/background
            }
        }
    }

    private fun showScheduledMedDetailsDialog(scheduledMed: ScheduledMedication) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_scheduled_med_details, null)
        val ivMedImage = dialogView.findViewById<ImageView>(R.id.ivDialogMedImage)
        val tvMedName = dialogView.findViewById<TextView>(R.id.tvDialogMedName)
        val tvMedQuantity = dialogView.findViewById<TextView>(R.id.tvDialogMedQuantity)
        val tvMedTime = dialogView.findViewById<TextView>(R.id.tvDialogMedTime)
        val tvMedInstructions = dialogView.findViewById<TextView>(R.id.tvDialogMedInstructions)
        val tvMedNotes = dialogView.findViewById<TextView>(R.id.tvDialogMedNotes)
        val btnMarkAsTaken = dialogView.findViewById<Button>(R.id.btnDialogMarkAsTaken)
        val btnRemoveSchedule = dialogView.findViewById<Button>(R.id.btnDialogRemoveSchedule)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        tvMedName.text = "${scheduledMed.nomeMedicamento} ${scheduledMed.dosagemMedicamento ?: ""}"
        tvMedQuantity.text = "Quantidade: ${scheduledMed.quantidadeATomar}"
        val timeFormat = SimpleDateFormat("HH:mm'h'", Locale.getDefault())
        tvMedTime.text = "Horário: ${timeFormat.format(Date(scheduledMed.scheduledTimestamp))}"
        if (!scheduledMed.imagemUrlMedicamento.isNullOrBlank()) {
            Glide.with(this)
                .load(scheduledMed.imagemUrlMedicamento)
                .placeholder(R.drawable.ic_placeholder_med)
                .error(R.drawable.ic_placeholder_error_med)
                .into(ivMedImage)
        } else {
            ivMedImage.setImageResource(R.drawable.ic_default_med_icon)
        }
        if (!scheduledMed.originalInstrucoes.isNullOrBlank()) {
            tvMedInstructions.text = "Instruções: ${scheduledMed.originalInstrucoes}"
            tvMedInstructions.visibility = View.VISIBLE
        } else {
            tvMedInstructions.visibility = View.GONE
        }
        if (!scheduledMed.originalNotas.isNullOrBlank()) {
            tvMedNotes.text = "Notas: ${scheduledMed.originalNotas}"
            tvMedNotes.visibility = View.VISIBLE
        } else {
            tvMedNotes.visibility = View.GONE
        }
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        if (scheduledMed.status.equals("Por tomar", ignoreCase = true)) {
            btnMarkAsTaken.visibility = View.VISIBLE
            btnMarkAsTaken.setOnClickListener {
                marcarComoTomado(scheduledMed)
                dialog.dismiss()
            }
        } else {
            btnMarkAsTaken.visibility = View.GONE
        }
        btnRemoveSchedule.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Remover Agendamento")
                .setMessage("Tem a certeza que quer remover este agendamento de ${scheduledMed.nomeMedicamento}?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Remover") { _: DialogInterface, _: Int ->
                    removerAgendamento(scheduledMed)
                    dialog.dismiss()
                }
                .show()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun marcarComoTomado(scheduledMed: ScheduledMedication) {
        if (scheduledMed.id == null) {
            Toast.makeText(this, "Erro: ID do agendamento não encontrado.", Toast.LENGTH_SHORT).show()
            return
        }
        val updates = mapOf("status" to "Tomado")
        dbFirestore.collection("user_medication_schedules").document(scheduledMed.id!!)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "${scheduledMed.nomeMedicamento} marcado como tomado!", Toast.LENGTH_SHORT).show()
                loadTodaysMedications()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao marcar como tomado: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    private fun removerAgendamento(scheduledMed: ScheduledMedication) {
        if (scheduledMed.id == null) {
            Toast.makeText(this, "Erro: ID do agendamento não encontrado para remover.", Toast.LENGTH_SHORT).show()
            return
        }
        dbFirestore.collection("user_medication_schedules").document(scheduledMed.id!!)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "${scheduledMed.nomeMedicamento} removido do horário.", Toast.LENGTH_SHORT).show()
                loadTodaysMedications()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao remover agendamento: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun scrollToPreviousMed() {
        val layoutManager = rvNextMeds.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        if (firstVisible > 0) {
            layoutManager.smoothScrollToPosition(rvNextMeds, null, firstVisible - 1)
        }
    }

    private fun scrollToNextMed() {
        val layoutManager = rvNextMeds.layoutManager as? LinearLayoutManager ?: return
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val itemCount = nextMedsAdapter.itemCount
        if (lastVisible < itemCount - 1) {
            layoutManager.smoothScrollToPosition(rvNextMeds, null, lastVisible + 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (countdownRunnable != null) handler.removeCallbacks(countdownRunnable!!)
    }
}
