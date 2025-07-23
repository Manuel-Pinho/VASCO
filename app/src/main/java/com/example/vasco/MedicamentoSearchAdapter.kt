package com.example.vasco.adapter // Ou o teu pacote de adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vasco.R
import com.example.vasco.model.MedicamentoFB
// Importa uma biblioteca de carregamento de imagens como Glide ou Picasso
// Exemplo com Glide (precisarás da dependência)
import com.bumptech.glide.Glide
import androidx.core.content.ContextCompat

class MedicamentoSearchAdapter(
    medicamentos: List<MedicamentoFB>,
    private val onAddClick: (MedicamentoFB) -> Unit,
    private var favouriteIds: Set<String> = emptySet(),
    private val onFavouriteToggle: (MedicamentoFB, Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<MedicamentoSearchAdapter.ViewHolder>() {

    var medicamentos: List<MedicamentoFB> = medicamentos
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val medicamento = medicamentos[position]
        val isFavourite = favouriteIds.contains(medicamento.nome) // or medicamento.id if available
        holder.bind(medicamento, isFavourite, onAddClick, onFavouriteToggle)
    }

    override fun getItemCount(): Int = medicamentos.size

    fun updateData(newMedicamentos: List<MedicamentoFB>, newFavourites: Set<String>? = null) {
        this.medicamentos = newMedicamentos
        if (newFavourites != null) this.favouriteIds = newFavourites
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImagem: ImageView = itemView.findViewById(R.id.ivMedicamentoImagem)
        private val tvNome: TextView = itemView.findViewById(R.id.tvMedicamentoNome)
        private val btnAdd: ImageButton = itemView.findViewById(R.id.btnAddMedicamentoToList)
        private val btnStar: ImageButton = itemView.findViewById(R.id.btnAddFavoutireToList)

        fun bind(
            medicamento: MedicamentoFB,
            isFavourite: Boolean,
            onAddClick: (MedicamentoFB) -> Unit,
            onFavouriteToggle: (MedicamentoFB, Boolean) -> Unit
        ) {
            tvNome.text = medicamento.nome

            // Carregar imagem com Glide (ou Picasso)
            if (!medicamento.imagemUrl.isNullOrEmpty()) { // Supondo que tens 'imagemUrl' no MedicamentoFB
                Glide.with(itemView.context)
                    .load(medicamento.imagemUrl)
                    .placeholder(R.mipmap.ic_launcher) // Imagem enquanto carrega
                    .error(R.mipmap.ic_launcher_round) // Imagem se der erro
                    .into(ivImagem)
            } else {
                // Imagem default se não houver URL
                ivImagem.setImageResource(R.mipmap.ic_launcher)
            }

            btnAdd.setOnClickListener {
                onAddClick(medicamento)
            }

            btnStar.setImageResource(
                if (isFavourite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
            )
            btnStar.setColorFilter(
                if (isFavourite)
                    ContextCompat.getColor(itemView.context, R.color.gold)
                else
                    ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
            )
            btnStar.setOnClickListener {
                onFavouriteToggle(medicamento, !isFavourite)
            }
        }
    }
}