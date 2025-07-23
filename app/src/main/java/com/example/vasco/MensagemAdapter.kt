package com.example.vasco

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MensagemAdapter(private val mensagens: List<Mensagem>) :
    RecyclerView.Adapter<MensagemAdapter.MensagemViewHolder>() {

    class MensagemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout = view.findViewById(R.id.mensagemContainer)
        val txtConteudo: TextView = view.findViewById(R.id.txtConteudo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MensagemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mensagem, parent, false)
        return MensagemViewHolder(view)
    }

    override fun onBindViewHolder(holder: MensagemViewHolder, position: Int) {
        val mensagem = mensagens[position]
        holder.txtConteudo.text = mensagem.conteudo

        if (mensagem.remetente == "user") {
            holder.container.gravity = Gravity.END
            holder.txtConteudo.setBackgroundResource(R.drawable.bg_mensagem_user)
        } else {
            holder.container.gravity = Gravity.START
            holder.txtConteudo.setBackgroundResource(R.drawable.bg_mensagem_vasco)
        }
    }

    override fun getItemCount(): Int = mensagens.size
}
