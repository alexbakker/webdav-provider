package me.alexbakker.webdav.fragments

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import me.alexbakker.webdav.BuildConfig
import me.alexbakker.webdav.R
import me.alexbakker.webdav.adapters.AccountAdapter
import me.alexbakker.webdav.databinding.FragmentMainBinding
import me.alexbakker.webdav.settings.Account
import me.alexbakker.webdav.settings.Settings
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : Fragment() {
    @Inject
    lateinit var settings: Settings

    private var actionMode: ActionMode? = null
    private lateinit var binding : FragmentMainBinding
    private lateinit var accountAdapter: AccountAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        accountAdapter = AccountAdapter(settings.accounts, Listener())
        binding.rvAccounts.layoutManager = LinearLayoutManager(context)
        binding.rvAccounts.adapter = accountAdapter

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<AccountFragment.Result?>("result")?.observe(
            viewLifecycleOwner
        ) { result ->
            when (result.action) {
                AccountFragment.Action.REMOVE -> {
                    removeAccount(result.uuid)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val empty = settings.accounts.isEmpty()
        binding.viewEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvAccounts.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun removeAccount(uuid: UUID) {
        settings.save(requireContext())
        accountAdapter.remove(uuid)
    }

    private inner class Listener : AccountAdapter.Listener {
        override fun onAccountClick(account: Account) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(account.rootUri, DocumentsContract.Document.MIME_TYPE_DIR)

            if (intent.resolveActivityInfo(requireContext().packageManager, 0) != null) {
                startActivity(intent)
            } else {
                val action = MainFragmentDirections.actionMainFragmentToAccountFragment(
                    account.uuid,
                    getString(R.string.edit_account)
                )

                findNavController().navigate(action)
            }
        }

        override fun onAccountLongClick(account: Account): Boolean {
            actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(ActionModeListener())
            return true
        }
    }

    private inner class ActionModeListener : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            val inflater: MenuInflater = mode!!.menuInflater
            inflater.inflate(R.menu.menu_account_action, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            /*if (item!!.itemId == R.id.action_delete) {
                removeAccount()
            }*/

            actionMode!!.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
        }
    }
}
