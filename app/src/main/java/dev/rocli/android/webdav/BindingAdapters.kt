package dev.rocli.android.webdav

import android.widget.AutoCompleteTextView
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import dev.rocli.android.webdav.data.Account
import dev.rocli.android.webdav.data.SecretString

object BindingAdapters {
    @JvmStatic
    @BindingAdapter("android:valueAttrChanged")
    fun setSliderListeners(slider: Slider, attrChange: InverseBindingListener) {
        slider.addOnChangeListener { _, _, _ ->
            attrChange.onChange()
        }
    }

    @JvmStatic
    @BindingAdapter("android:value")
    fun setSliderValueLong(slider: Slider, newValue: Long) {
        val fNewValue = newValue.toFloat()
        if (slider.value != fNewValue) {
            slider.value = fNewValue
        }
    }

    @JvmStatic
    @InverseBindingAdapter(attribute = "android:value")
    fun getSliderValueLong(slider: Slider): Long {
        return slider.value.toLong()
    }

    @JvmStatic
    @BindingAdapter("android:text")
    fun setDropdownValueProtocol(view: AutoCompleteTextView, newValue: Account.Protocol) {
        val array = view.resources!!.getStringArray(R.array.protocol_options)
        val text = array[newValue.ordinal]
        if (view.text.toString() != text) {
            view.setText(text, false)
        }
    }

    @JvmStatic
    @InverseBindingAdapter(attribute = "android:text")
    fun getDropdownValueProtocol(view: AutoCompleteTextView): Account.Protocol {
        val array = view.resources!!.getStringArray(R.array.protocol_options)
        return Account.Protocol.entries[array.indexOf(view.text.toString())]
    }

    @JvmStatic
    @BindingAdapter("android:text")
    fun setDropdownValueAuthType(view: AutoCompleteTextView, newValue: Account.AuthType) {
        val array = view.resources!!.getStringArray(R.array.auth_type_options)
        val text = array[newValue.ordinal]
        if (view.text.toString() != text) {
            view.setText(text, false)
        }
    }

    @JvmStatic
    @InverseBindingAdapter(attribute = "android:text")
    fun getDropdownValueAuthType(view: AutoCompleteTextView): Account.AuthType {
        val array = view.resources!!.getStringArray(R.array.auth_type_options)
        return Account.AuthType.entries[array.indexOf(view.text.toString())]
    }

    @JvmStatic
    @BindingAdapter("android:text")
    fun setSecretStringValue(view: TextInputEditText, newValue: SecretString?) {
        view.setText(newValue?.value)
    }

    @JvmStatic
    @InverseBindingAdapter(attribute = "android:text")
    fun getSecretStringValue(view: TextInputEditText): SecretString? {
        if (view.text.isNullOrBlank()) {
            return null
        }

        return SecretString(view.text.toString())
    }
}

