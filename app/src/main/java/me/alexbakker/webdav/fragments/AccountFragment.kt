package me.alexbakker.webdav.fragments

import android.os.Bundle
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.alexbakker.webdav.R
import me.alexbakker.webdav.data.Account
import me.alexbakker.webdav.data.AccountDao
import me.alexbakker.webdav.databinding.FragmentAccountBinding
import me.alexbakker.webdav.dialogs.Dialogs
import me.alexbakker.webdav.provider.WebDavCache
import me.alexbakker.webdav.provider.WebDavProvider
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment() {
    @Inject
    lateinit var accountDao: AccountDao

    @Inject
    lateinit var webDavCache: WebDavCache

    private lateinit var menu: Menu

    private val args: AccountFragmentArgs by navArgs()
    private lateinit var binding: FragmentAccountBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_account, container, false)
        if (args.id != -1L) {
            binding.account = accountDao.getById(args.id).copy()
        } else {
            binding.account = Account()
        }

        binding.sliderMaxCacheFileSize.apply {
            setLabelFormatter { getString(R.string.value_max_cache_file_size, it.toInt()) }
            addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
                // TODO: figure out why this update doesn't automatically happen with data binding
                binding.valueCacheFileSize.text = getString(R.string.value_max_cache_file_size, value.toLong())
            })
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
        if (args.id == -1L) {
            menu.findItem(R.id.action_delete).isVisible = false
        }
        this.menu = menu
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val account = binding.account!!

        when (item.itemId) {
            R.id.action_save -> {
                if (validateForm()) {
                    updateTestStatus(true)
                    val job = lifecycleScope.launch(Dispatchers.IO) {
                        account.resetState()
                        val res = account.client.propFind(account.root.path)
                        if (res.isSuccessful) {
                            webDavCache.setRoot(account, res.body!!)
                            if (account.id == 0L) {
                                accountDao.insert(account)
                            } else {
                                accountDao.update(account)
                            }

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
                                updateTestStatus(false)
                            }
                        }
                    }
                }
            }
            R.id.action_delete -> {
                Dialogs.showRemoveAccountsDialog(requireContext(), listOf(account)) { _, _ ->
                    accountDao.delete(account)

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
        val origAccount = if (args.id == -1L) Account() else accountDao.getById(args.id)
        val formAccount = binding.account!!.copy(id = origAccount.id)

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

    private fun updateTestStatus(testing: Boolean) {
        setTitle(if (testing) getString(R.string.webdav_testing_connection) else args.title)
        menu.setGroupEnabled(R.id.menu_action_group, !testing)
        setIsLayoutEnabled(binding.layoutForm, !testing)
        binding.progressIndicator.visibility = if (testing) View.VISIBLE else View.INVISIBLE
    }

    private fun setTitle(title: String) {
        (requireActivity() as AppCompatActivity).supportActionBar!!.title = title
    }

    private fun setIsLayoutEnabled(group: ViewGroup, enabled: Boolean) {
        for (child in group.children) {
            child.isEnabled = enabled
            if (child is ViewGroup) {
                setIsLayoutEnabled(child, enabled)
            }
        }
    }

    private inner class BackPressedCallback : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (tryClose() && isEnabled) {
                isEnabled = false
            }
        }
    }

    companion object {
        @BindingAdapter("android:valueAttrChanged")
        @JvmStatic fun setSliderListeners(slider: Slider, attrChange: InverseBindingListener) {
            slider.addOnChangeListener { _, _, _ ->
                attrChange.onChange()
            }
        }

        @BindingAdapter("android:value")
        @JvmStatic fun setSliderValueLong(view: Slider, newValue: Long) {
            val fNewValue = newValue.toFloat()
            if (view.value != fNewValue) {
                view.value = fNewValue
            }
        }

        @InverseBindingAdapter(attribute = "android:value")
        @JvmStatic fun getSliderValueLong(view: Slider): Long {
            return view.value.toLong()
        }
    }
}
