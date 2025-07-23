package com.example.vasco

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.example.vasco.adapter.DayCalendarAdapter
import com.example.vasco.adapter.DayItem
import com.example.vasco.adapter.DayState
import com.example.vasco.adapter.MedicamentoSearchAdapter
import com.example.vasco.adapter.ScheduledMedicationAdapter
import com.example.vasco.model.MedicamentoFB
import com.example.vasco.model.ScheduledMedication
import com.example.vasco.util.ScheduleParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.bumptech.glide.Glide
import android.widget.LinearLayout
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.viewpager2.widget.ViewPager2
import android.view.ViewGroup

class MainNormalActivity : ToolbarActivity() {

    private lateinit var rvDaysHorizontal: RecyclerView
    private lateinit var dayCalendarAdapter: DayCalendarAdapter
    private lateinit var tvMonthName: TextView
    private lateinit var ivCalendarIcon: ImageView
    private lateinit var btnPrevDayScroll: ImageButton
    private lateinit var btnNextDayScroll: ImageButton
    private lateinit var fabAddMedicamento: FloatingActionButton
    private lateinit var searchResultsAdapter: MedicamentoSearchAdapter
    private var searchDialog: AlertDialog? = null
    private lateinit var rvScheduledMeds: RecyclerView
    private lateinit var scheduledMedAdapter: ScheduledMedicationAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var dbFirestore: FirebaseFirestore
    private lateinit var viewPager: ViewPager2
    private var currentPage = 1000 // Start at middle of a large range
    private val totalPages = 2000 // Total number of pages (1000 before and 1000 after)

    private val calendar = Calendar.getInstance() // Dia principal selecionado na aplicação
    private val visibleDaysCount = 5 // Quantos dias mostrar no RecyclerView horizontal
    private val middleAdapterIndex = visibleDaysCount / 2 // Posição do meio no adapter (ex: 3 para 7 itens)

    private lateinit var gestureDetector: GestureDetectorCompat // Para swipes

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_normal)

        // Initialize Firebase first
        auth = FirebaseAuth.getInstance()
        dbFirestore = Firebase.firestore

        // AVISO SOBRE ALARMES EXATOS (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!am.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "Para receber notificações na hora certa, ative a permissão de alarmes exatos nas definições do sistema.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Initialize all views
        initializeViews()

        // Setup components
        setupComponents()

        // Update calendar data
        updateCalendarData()
    }

    private fun initializeViews() {
        // Initialize views that exist in the main layout
        fabAddMedicamento = findViewById(R.id.fabAddMedicamento)
        viewPager = findViewById(R.id.viewPager)
        tvMonthName = findViewById(R.id.tvMonthName)
        ivCalendarIcon = findViewById(R.id.ivCalendarIcon)
        rvDaysHorizontal = findViewById(R.id.rvDaysHorizontal)
        btnPrevDayScroll = findViewById(R.id.btnPrevDayScroll)
        btnNextDayScroll = findViewById(R.id.btnNextDayScroll)
    }

    private fun setupComponents() {
        setupToolbarClicks()
        setupCalendarView()
        setupViewPager()
        fabAddMedicamento.setOnClickListener { showSearchMedicamentoDialog() }
    }

    private fun setupCalendarView() {
        // Setup month name
        updateMonthName()

        // Setup calendar icon
        ivCalendarIcon.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecione uma data")
                .setSelection(calendar.timeInMillis)
                .build()
            datePicker.addOnPositiveButtonClickListener { selection ->
                calendar.timeInMillis = selection
                updateCalendarData()
            }
            datePicker.show(supportFragmentManager, "MATERIAL_DATE_PICKER")
        }

        // Setup horizontal day selector
        rvDaysHorizontal.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        updateDaysList()

        // Setup navigation buttons
        btnPrevDayScroll.setOnClickListener {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            currentPage--
            viewPager.setCurrentItem(currentPage, true)
            updateCalendarData()
        }
        btnNextDayScroll.setOnClickListener {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            currentPage++
            viewPager.setCurrentItem(currentPage, true)
            updateCalendarData()
        }
    }

    private fun updateMonthName() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("pt", "PT"))
        val currentMonthName = monthFormat.format(calendar.time).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale("pt", "PT")) else it.toString()
        }
        tvMonthName.text = currentMonthName
    }

    private fun updateDaysList() {
        val daysList = mutableListOf<DayItem>()
        val dayLetterFormat = SimpleDateFormat("E", Locale("pt", "PT"))
        val dayNumberFormat = SimpleDateFormat("d", Locale.getDefault())
        val todayCal = Calendar.getInstance()

        for (i in 0 until visibleDaysCount) {
            val dayCal = calendar.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, i - middleAdapterIndex)
            val dayLetter = dayLetterFormat.format(dayCal.time).first().uppercase()
            val dayNumber = dayNumberFormat.format(dayCal.time)
            val fullDate = dayCal.time

            val state = when {
                isSameDay(dayCal, calendar) -> DayState.SELECTED_RED
                isSameDay(dayCal, todayCal) -> DayState.SELECTED_GREEN
                else -> DayState.NORMAL
            }
            daysList.add(DayItem(dayLetter, dayNumber, fullDate, state))
        }

        val dayAdapter = DayCalendarAdapter(daysList) { dayItemClicked ->
            // Calculate the page offset for the clicked day
            val clickedDate = dayItemClicked.fullDate
            val currentDate = calendar.time
            val diffInDays = ((clickedDate.time - currentDate.time) / (1000 * 60 * 60 * 24)).toInt()
            
            // Update the calendar
            calendar.time = clickedDate
            
            // Update the ViewPager position
            currentPage += diffInDays
            viewPager.setCurrentItem(currentPage, true)
            
            // Update the UI
            updateCalendarData()
        }
        rvDaysHorizontal.adapter = dayAdapter
    }

    private fun setupViewPager() {
        viewPager.adapter = object : RecyclerView.Adapter<DayViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.view_day_content, parent, false)
                return DayViewHolder(view)
            }

            override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
                val view = holder.itemView
                val rvScheduledMedsToday = view.findViewById<RecyclerView>(R.id.rvScheduledMedsToday)

                // Calculate the date for this position
                val tempCalendar = calendar.clone() as Calendar
                tempCalendar.add(Calendar.DAY_OF_YEAR, position - currentPage)

                // Setup RecyclerView
                rvScheduledMedsToday.layoutManager = LinearLayoutManager(view.context)

                // Initialize scheduled medications adapter for this page
                val scheduledMedAdapter = ScheduledMedicationAdapter(emptyList()) { scheduledMed ->
                    showScheduledMedDetailsDialog(scheduledMed)
                }
                rvScheduledMedsToday.adapter = scheduledMedAdapter

                // Load scheduled medications for this day
                loadScheduledMedicationsForDay(tempCalendar.time, rvScheduledMedsToday)
            }

            override fun getItemCount(): Int = totalPages
        }

        // Set initial page
        viewPager.setCurrentItem(currentPage, false)

        // Handle page changes
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position != currentPage) {
                    val dayOffset = position - currentPage
                    calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
                    currentPage = position
                    updateCalendarData()
                }
            }
        })
    }

    private inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    fun updateCalendarData() {
        // Update the calendar views
        updateMonthName()
        updateDaysList()
        
        // Update the ViewPager
        viewPager.setCurrentItem(currentPage, true)
    }

    private fun loadScheduledMedicationsForDay(selectedDate: Date, recyclerView: RecyclerView) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d("LOAD_MEDS", "Utilizador não logado, não carregando agendamentos.")
            return
        }
        val userId = currentUser.uid

        val cal = Calendar.getInstance()
        cal.time = selectedDate
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDayTimestamp = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDayTimestamp = cal.timeInMillis

        dbFirestore.collection("user_medication_schedules")
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("scheduledTimestamp", startOfDayTimestamp)
            .whereLessThanOrEqualTo("scheduledTimestamp", endOfDayTimestamp)
            .orderBy("scheduledTimestamp")
            .get()
            .addOnSuccessListener { documents ->
                val medsDoDia = mutableListOf<ScheduledMedication>()
                if (documents != null && !documents.isEmpty) {
                    for (document in documents) {
                        try {
                            val scheduledMed = document.toObject(ScheduledMedication::class.java)
                            if (scheduledMed != null) {
                                medsDoDia.add(scheduledMed)
                            }
                        } catch (e: Exception) {
                            Log.e("LOAD_MEDS", "Erro ao converter documento ${document.id}", e)
                        }
                    }
                }
                val adapter = ScheduledMedicationAdapter(medsDoDia) { scheduledMed ->
                    showScheduledMedDetailsDialog(scheduledMed)
                }
                recyclerView.adapter = adapter
            }
            .addOnFailureListener { exception ->
                Log.e("LOAD_MEDS", "Erro ao carregar agendamentos do dia.", exception)
                recyclerView.adapter = ScheduledMedicationAdapter(emptyList()) { }
            }
    }

    private fun updateDayStatesInAdapter(selectedDate: Date) {
        // Esta função pode ser simplificada ou removida se 'updateCalendarData'
        // já reconstrói a lista de dias com os estados corretos.
        // Por agora, vamos assumir que updateCalendarData é a fonte da verdade para os estados.
        Log.d("STATE_UPDATE", "updateDayStatesInAdapter chamada para $selectedDate - pode ser redundante")
        // Se for para garantir que o item clicado fica vermelho IMEDIATAMENTE antes de
        // updateCalendarData() ser chamado por completo (o que pode ter um delay visual mínimo),
        // poderias manter uma lógica de atualização rápida aqui, mas geralmente não é preciso.
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    // Função temporária para resetar agendamentos (para testes)
    @Suppress("unused") // Para evitar aviso de não usado se não ligares a um botão visível
    private fun resetarAgendamentosDoUtilizador() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Utilizador não logado.", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = currentUser.uid

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar Reset")
            .setMessage("Tem a certeza que quer apagar TODOS os seus agendamentos de medicamentos?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar Tudo") { _, _ ->
                dbFirestore.collection("user_medication_schedules")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (documents.isEmpty) {
                            Toast.makeText(this, "Nenhum agendamento para apagar.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                        val batch = dbFirestore.batch()
                        for (document in documents) {
                            batch.delete(document.reference)
                        }
                        batch.commit()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Todos os agendamentos foram apagados!", Toast.LENGTH_SHORT).show()
                                loadScheduledMedicationsForDay(calendar.time, rvScheduledMeds)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Erro ao apagar agendamentos: ${e.message}", Toast.LENGTH_LONG).show()
                                Log.e("RESET_SCHEDULES", "Erro no batch delete", e)
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Erro ao procurar agendamentos para apagar: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("RESET_SCHEDULES", "Erro ao obter documentos para delete", e)
                    }
            }
            .show()
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

        // Preencher dados
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

        // Lógica dos botões
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
            // Se já foi tomado ou ignorado, esconde o botão "Marcar como Tomado"
            // Ou podes mudar o texto para "Marcar como Não Tomado" e implementar essa lógica
            btnMarkAsTaken.visibility = View.GONE
            // Se já tomado, podes querer desabilitar também o botão de remover, ou não.
        }


        btnRemoveSchedule.setOnClickListener {
            // Adicionar diálogo de confirmação para remover
            MaterialAlertDialogBuilder(this)
                .setTitle("Remover Agendamento")
                .setMessage("Tem a certeza que quer remover este agendamento de ${scheduledMed.nomeMedicamento}?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Remover") { _, _ ->
                    removerAgendamento(scheduledMed)
                    dialog.dismiss()
                }
                .show()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun marcarComoTomado(scheduledMed: ScheduledMedication) {
        if (scheduledMed.id == null) {
            Toast.makeText(this, "Erro: ID do agendamento não encontrado.", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mapOf(
            "status" to "Tomado"
        )

        dbFirestore.collection("user_medication_schedules").document(scheduledMed.id!!)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "${scheduledMed.nomeMedicamento} marcado como tomado!", Toast.LENGTH_SHORT).show()
                loadScheduledMedicationsForDay(Date(scheduledMed.scheduledTimestamp), rvScheduledMeds)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao marcar como tomado: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("MARK_TAKEN", "Erro", e)
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
                loadScheduledMedicationsForDay(Date(scheduledMed.scheduledTimestamp), rvScheduledMeds)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao remover agendamento: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("REMOVE_SCHEDULE", "Erro", e)
            }
    }

    private fun showSearchMedicamentoDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_medicamento, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchMedicamentoDialog)
        val rvResults = dialogView.findViewById<RecyclerView>(R.id.rvSearchResultsDialog)

        rvResults.layoutManager = LinearLayoutManager(this)
        searchResultsAdapter = MedicamentoSearchAdapter(
            emptyList(),
            onAddClick = { medicamentoFB ->
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(this, "Precisa estar logado para adicionar medicamentos.", Toast.LENGTH_SHORT).show()
                    return@MedicamentoSearchAdapter
                }
                val userId = currentUser.uid
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
                        Toast.makeText(this@MainNormalActivity, "${medicamentoFB.nome} já está agendado e 'Por tomar'.", Toast.LENGTH_LONG).show()
                        searchDialog?.dismiss()
                        return@launch
                    }
                    val novosAgendamentos = ScheduleParser.parsearInstrucoesESimularHorarios(medicamentoFB, userId, calendar.time)
                    if (novosAgendamentos.isEmpty()) {
                        val dataReferenciaFormatada = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)
                        Toast.makeText(
                            this@MainNormalActivity,
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
                                            this@MainNormalActivity,
                                            agendamentoParaSalvar.nomeMedicamento,
                                            agendamentoParaSalvar.scheduledTimestamp
                                        )
                                    }
                                    if (agendamentosSalvosComSucesso + falhasAoSalvar == totalAgendamentosParaProcessar) {
                                        if (falhasAoSalvar == 0) {
                                            Toast.makeText(this@MainNormalActivity, "${medicamentoFB.nome} adicionado ao seu horário!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(this@MainNormalActivity, "Adicionado ${medicamentoFB.nome} com ${falhasAoSalvar} erro(s) no agendamento.", Toast.LENGTH_LONG).show()
                                        }
                                        searchDialog?.dismiss()
                                        updateCalendarData()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    falhasAoSalvar++
                                    Log.e("ADD_SCHEDULE", "Erro ao salvar agendamento para ${medicamentoFB.nome}", e)
                                    if (agendamentosSalvosComSucesso + falhasAoSalvar == totalAgendamentosParaProcessar) {
                                        Toast.makeText(this@MainNormalActivity, "Erro ao adicionar ${medicamentoFB.nome}. ${falhasAoSalvar} falha(s).", Toast.LENGTH_LONG).show()
                                        searchDialog?.dismiss()
                                    }
                                }
                        }
                    }
                }
            },
            favouriteIds = emptySet(),
            onFavouriteToggle = { _, _ -> }
        )
        rvResults.adapter = searchResultsAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    pesquisarMedicamentosNoFirestore(query, searchResultsAdapter)
                } else {
                    searchResultsAdapter.updateData(emptyList())
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

    private fun pesquisarMedicamentosNoFirestore(query: String, adapter: MedicamentoSearchAdapter) {
        if (query.isEmpty()) {
            adapter.updateData(emptyList())
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
                adapter.updateData(resultados)
            }
            .addOnFailureListener { exception ->
                Log.e("FIRESTORE_SEARCH", "Erro ao pesquisar medicamentos.", exception)
                adapter.updateData(emptyList())
            }
    }
}

