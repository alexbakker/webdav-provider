package me.alexbakker.webdav.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import me.alexbakker.webdav.data.Account
import me.alexbakker.webdav.data.byId
import me.alexbakker.webdav.databinding.CardAccountBinding


class AccountAdapter(
    accounts: List<Account>,
    private val listener: Listener
    ) : RecyclerView.Adapter<AccountAdapter.Holder>() {
    val selectedAccounts: MutableList<Account> = ArrayList()
    private val accounts = ArrayList(accounts.sortedBy { it.name!!.lowercase() })

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = CardAccountBinding.inflate(inflater, parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val account = accounts[position]
        holder.itemView.setOnClickListener {
            when {
                selectedAccounts.size == 0 -> {
                    listener.onAccountClick(account)
                }
                selectedAccounts.contains(account) -> {
                    selectedAccounts.remove(account)
                    holder.selected = false
                    if (selectedAccounts.size == 0) {
                        clearSelection()
                    } else {
                        listener.onAccountSelectionChange(selectedAccounts)
                    }
                }
                else -> {
                    selectedAccounts.add(account)
                    holder.selected = true
                    listener.onAccountSelectionChange(selectedAccounts)
                }
            }
        }
        holder.itemView.setOnLongClickListener {
            if (selectedAccounts.size == 0) {
                selectedAccounts.add(account)
                holder.selected = true
                listener.onAccountSelectionStart()
                listener.onAccountSelectionChange(selectedAccounts)
                true
            } else {
                false
            }
        }
        holder.bind(account, selected = selectedAccounts.contains(account))
    }

    override fun getItemCount(): Int {
        return accounts.size
    }

    fun remove(account: Account) {
        val i = accounts.indexOf(accounts.byId(account.id))
        accounts.removeAt(i)
        notifyItemRemoved(i)
    }

    fun clearSelection() {
        selectedAccounts.clear()
        notifyDataSetChanged()
        listener.onAccountSelectionEnd()
    }

    class Holder(
        private val binding: CardAccountBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        var selected: Boolean = false
            set(value) {
                field = value
                (itemView as MaterialCardView).isChecked = value
            }

        fun bind(account: Account, selected: Boolean) {
            this.selected = selected
            binding.account = account
            binding.executePendingBindings()
        }
    }

    interface Listener {
        fun onAccountSelectionStart()
        fun onAccountSelectionChange(accounts: List<Account>)
        fun onAccountSelectionEnd()
        fun onAccountClick(account: Account)
    }
}
