package com.four.base.zy.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import com.four.base.zy.IViewFinder
import com.four.common_util.permission.PermissionHelper

abstract class BaseActivity : AppCompatActivity(), IViewFinder {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createView())
        if (hideActionBar()) {
            supportActionBar?.hide()
        }
    }

    open fun createView() : View {
        val inflater = LayoutInflater.from(this)
        return inflater.inflate(getLayoutId(),
            window.decorView as ViewGroup, false)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun <V : View> findView(id: Int): V? = findViewById<V>(id)

    override fun <V : View> findViewNoNull(id: Int) = this.findViewById<V>(id)!!

    protected open fun hideActionBar() = true

    @LayoutRes
    protected abstract fun getLayoutId() : Int
}