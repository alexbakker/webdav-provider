package me.alexbakker.webdav.fragments

import android.os.Bundle
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.alexbakker.webdav.R
import me.alexbakker.webdav.databinding.FragmentAccountBinding
import me.alexbakker.webdav.provider.WebDavProvider
import me.alexbakker.webdav.settings.Account
import me.alexbakker.webdav.settings.Settings
import me.alexbakker.webdav.settings.byUUID
import me.alexbakker.webdav.settings.byUUIDOrNull
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment() {
    @Inject
    lateinit var settings: Settings

    private lateinit var menu: Menu

    private val args: AccountFragmentArgs by navArgs()
    private lateinit var binding: FragmentAccountBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_account, container, false)
        if (args.uuid != null) {
            binding.account = settings.accounts.byUUID(args.uuid!!).copy()
        } else {
            binding.account = Account()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, BackPressedCallback())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_account, menu)
        if (args.uuid == null) {
            menu.findItem(R.id.action_delete).isVisible = false
        }
        this.menu = menu
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val account = binding.account!!

        when (item.itemId) {
            R.id.action_save -> {
                if (validateForm()) {
                    menu.setGroupEnabled(R.id.menu_action_group, false)
                    binding.busyIndicator.visibility = View.VISIBLE

                    val job = lifecycleScope.launch(Dispatchers.IO) {
                        account.resetState()
                        val res = account.client.propFind(account.root.path)
                        if (res.isSuccessful) {
                            account.root = res.body!!
                            val oldAccount = settings.accounts.byUUIDOrNull(account.uuid)
                            if (oldAccount == null) {
                                settings.accounts.add(account)
                            } else {
                                settings.accounts[settings.accounts.indexOf(oldAccount)] = account
                            }
                            settings.save(requireContext())

                            WebDavProvider.notifyChangeRoots(requireContext())
                            lifecycleScope.launch(Dispatchers.Main) { close() }
                        } else {
                            lifecycleScope.launch(Dispatchers.Main) {
                                Snackbar
                                    .make(
                                        requireView(),
                                        getString(R.string.error_webdav_connection, res.error?.message),
                                        BaseTransientBottomBar.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        }
                    }
                    job.invokeOnCompletion {
                        if (it == null) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                menu.setGroupEnabled(R.id.menu_action_group, true)
                                binding.busyIndicator.visibility = View.GONE
                            }
                        }
                    }
                }
            }
            R.id.action_delete -> {
                Dialogs.showRemoveAccountsDialog(requireContext(), listOf(account)) { _, _ ->
                    settings.accounts.remove(settings.accounts.byUUID(args.uuid!!))
                    settings.save(requireContext())

                    WebDavProvider.notifyChangeRoots(requireContext())
                    close()
                }
            }
            android.R.id.home -> {
                tryClose()
            }
            else -> {
                return false
            }
        }

        return true
    }

    private fun validateForm(): Boolean {
        var res = true
        val requiredTextFields = arrayOf(binding.textName, binding.textUrl)
        for (field in requiredTextFields) {
            if (field.text.toString().isBlank()) {
                (field.parent.parent as TextInputLayout).error = getString(R.string.error_field_required)
                res = false
            }
        }

        return res
    }

    private fun tryClose(): Boolean {
        val origAccount = if (args.uuid == null) Account() else settings.accounts.byUUID(args.uuid!!)
        val formAccount = binding.account!!.copy(uuid = origAccount.uuid)

        if (origAccount != formAccount) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_title_discard_changes)
                .setMessage(R.string.dialog_message_discard_changes)
                .setPositiveButton(R.string.yes) { _, _ ->
                    close()
                }
                .setNegativeButton(R.string.no, null)
                .show()
            return false
        }

        close()
        return true
    }

    private fun close() {
        view?.clearFocus()
        findNavController().popBackStack()
    }

    private inner class BackPressedCallback : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (tryClose() && isEnabled) {
                isEnabled = false
            }
        }
    }
}
