package com.khanproxy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TokenAdapter : RecyclerView.Adapter<TokenAdapter.VH>() {
    private val items = mutableListOf<TokenEntry>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun submit(list: List<TokenEntry>) {
        try {
            items.clear(); items.addAll(list); notifyDataSetChanged()
        } catch (_: Exception) {}
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_token, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        try {
            val e = items[position]
            holder.tvMode.text = e.mode
            holder.tvMode.setTextColor(
                if (e.mode == "JWT") Color.parseColor("#00E676")
                else Color.parseColor("#9C4DFF"))
            holder.tvRegion.text = "● ${e.region}"
            holder.tvTime.text = fmt.format(Date(e.timestamp))
            holder.tvPlayer.text = "Player: ${e.player.ifEmpty { "—" }}"

            val tokenText = if (e.mode == "JWT") e.jwt
                           else "OpenID: ${e.openId}\nAccessToken: ${e.accessToken}"
            holder.tvToken.text = tokenText

            // COPY BUTTON
            holder.btnCopy.setOnClickListener {
                try {
                    val cm = holder.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("token", tokenText))
                    Toast.makeText(holder.itemView.context, "✔ Copied", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    override fun getItemCount() = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvMode: TextView = v.findViewById(R.id.tvMode)
        val tvRegion: TextView = v.findViewById(R.id.tvRegion)
        val tvTime: TextView = v.findViewById(R.id.tvTime)
        val tvPlayer: TextView = v.findViewById(R.id.tvPlayer)
        val tvToken: TextView = v.findViewById(R.id.tvToken)
        val btnCopy: TextView = v.findViewById(R.id.btnCopy)
    }
}
