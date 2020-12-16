package me.alexbakker.webdav.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.alexbakker.webdav.databinding.CardAccountBinding
import me.alexbakker.webdav.settings.Account
import me.alexbakker.webdav.settings.byUUID
import java.util.*

class AccountAdapter(
        private val accounts: MutableList<Account>,
        private val listener: Listener
    ) : RecyclerView.Adapter<AccountAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = CardAccountBinding.inflate(inflater, parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val account = accounts[position]
        holder.itemView.setOnClickListener {
            listener.onAccountClick(account)
        }
        holder.itemView.setOnLongClickListener {
            listener.onAccountLongClick(account)
        }
        holder.bind(account)
    }

    override fun getItemCount(): Int {
        return accounts.size
    }

    fun add(account: Account) {
        accounts.add(account)
        notifyItemInserted(accounts.size - 1)
    }

    fun update(account: Account) {
        val old = accounts.byUUID(account.uuid)
        val i = accounts.indexOf(old)
        accounts[i] = account
        notifyItemChanged(i)
    }

    fun remove(account: Account) {
        remove(account.uuid)
    }

    fun remove(uuid: UUID) {
        val i = accounts.indexOf(accounts.byUUID(uuid))
        accounts.removeAt(i)
        notifyItemRemoved(i)
    }

    class Holder(
        private val binding: CardAccountBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(account: Account) {
            binding.account = account
            binding.executePendingBindings()
        }
    }

    interface Listener {
        fun onAccountClick(account: Account)
        fun onAccountLongClick(account: Account): Boolean
    }
}
