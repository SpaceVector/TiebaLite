package com.huanchengfly.tieba.post.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import butterknife.ButterKnife
import butterknife.Unbinder
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.interfaces.BackHandledInterface
import com.huanchengfly.tieba.post.interfaces.Refreshable
import com.huanchengfly.tieba.post.isLandscape
import com.huanchengfly.tieba.post.isPortrait
import com.huanchengfly.tieba.post.isTablet
import com.huanchengfly.tieba.post.ui.common.theme.utils.ThemeUtils
import com.huanchengfly.tieba.post.utils.AppPreferencesUtils
import com.huanchengfly.tieba.post.utils.DialogUtil
import com.huanchengfly.tieba.post.utils.HandleBackUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

/**
 * Fragment 基类，封装了基础上下文获取、返回处理与首次可见/可见状态分发。
 *
 * @see .onFragmentVisibleChange
 * @see .onFragmentFirstVisible
 */
abstract class BaseFragment : Fragment(), BackHandledInterface, CoroutineScope {
    val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    var unbinder: Unbinder? = null

    protected var isFragmentVisible = false
        private set
    private var isReuseView = false
    var isFirstVisible = false
        private set
    private var rootView: View? = null
    var attachContextWeakReference: WeakReference<Context>? = null
    val attachContext: Context
        get() {
            var mContext: Context? = context
            if (mContext == null && attachContextWeakReference != null) {
                mContext = attachContextWeakReference!!.get()
                ThemeUtils.getWrapperActivity(mContext)?.let { mContext = it }
            }
            if (mContext == null) {
                mContext = App.INSTANCE
            }
            return mContext!!
        }
    protected val appPreferences: AppPreferencesUtils
        get() = AppPreferencesUtils.getInstance(attachContext)

    @RequiresApi(23)
    override fun onAttach(context: Context) {
        super.onAttach(context)
        onAttachToContext(context)
    }

    @CallSuper
    private fun onAttachToContext(context: Context) {
        attachContextWeakReference = WeakReference(context)
    }

    override fun onBackPressed(): Boolean {
        return HandleBackUtil.handleBackPress(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initVariable()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (rootView == null) {
            rootView = view
            dispatchFragmentVisibilityIfNeeded()
        }
        super.onViewCreated((if (isReuseView) rootView else view)!!, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        dispatchFragmentVisibilityIfNeeded()
    }

    override fun onPause() {
        if (isFragmentVisible) {
            isFragmentVisible = false
            onFragmentVisibleChange(false)
        }
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            if (isFragmentVisible) {
                isFragmentVisible = false
                onFragmentVisibleChange(false)
            }
        } else {
            dispatchFragmentVisibilityIfNeeded()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        initVariable()
    }

    private fun initVariable() {
        isFirstVisible = true
        isFragmentVisible = false
        rootView = null
        isReuseView = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val inflate = inflater.inflate(getLayoutId(), container, false)
        unbinder = ButterKnife.bind(this, inflate)
        return inflate
    }


    abstract fun getLayoutId(): Int

    /**
     * 设置是否使用 view 的复用，默认开启
     * view 的复用是指，ViewPager 在销毁和重建 Fragment 时会不断调用 onCreateView() -> onDestroyView()
     * 之间的生命函数，这样可能会出现重复创建 view 的情况，导致界面上显示多个相同的 Fragment
     * view 的复用其实就是指保存第一次创建的 view，后面再 onCreateView() 时直接返回第一次创建的 view
     *
     * @param isReuse 是否使用 view 的复用
     */
    protected fun reuseView(isReuse: Boolean) {
        isReuseView = isReuse
    }

    private fun dispatchFragmentVisibilityIfNeeded() {
        if (rootView == null || !isResumed || isHidden) {
            return
        }
        if (isFirstVisible) {
            onFragmentFirstVisible()
            isFirstVisible = false
        }
        if (!isFragmentVisible) {
            isFragmentVisible = true
            onFragmentVisibleChange(true)
        }
    }

    /**
     * 当 Fragment 可见状态发生变化时回调。回调时机保证在 View 创建完成之后。
     *
     * @param isVisible true  不可见 -> 可见
     * false 可见  -> 不可见
     */
    protected open fun onFragmentVisibleChange(isVisible: Boolean) {}

    /**
     * 在 Fragment 首次进入前台可见时回调，可在这里进行一次性数据加载。
     */
    protected open fun onFragmentFirstVisible() {}
    open fun onAccountSwitch() {
        if (this is Refreshable) {
            (this as Refreshable).onRefresh()
        }
    }

    protected val isTablet: Boolean
        get() = attachContext.isTablet

    protected val isPortrait: Boolean
        get() = attachContext.resources.configuration.isPortrait

    protected val isLandscape: Boolean
        get() = attachContext.resources.configuration.isLandscape

    open fun hasOwnAppbar(): Boolean {
        return false
    }

    fun showDialog(builder: AlertDialog.Builder.() -> Unit): AlertDialog {
        val dialog = DialogUtil.build(attachContext)
            .apply(builder)
            .create()
        dialog.show()
        return dialog
    }

    fun launchIO(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return launch(IO + job, start, block)
    }

    companion object {
        private val TAG = BaseFragment::class.java.simpleName
    }
}
