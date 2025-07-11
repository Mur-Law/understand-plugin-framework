package com.weishu.upf.ams_pms_hook.app;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * @author weishu
 * @date 16/3/7
 */
public final class HookHelper {
    //这个测试在安卓10到13还可以使用
     public static void hookActivityManager_android10() {
try {
            //取消 反射限制 
            Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");
            Method getRuntimeMethod = vmRuntimeClass.getDeclaredMethod("getRuntime");
            Object vmRuntime = getRuntimeMethod.invoke(null);
            Method setHiddenApiExemptionsMethod = vmRuntimeClass.getDeclaredMethod(
                    "setHiddenApiExemptions", String[].class);
            setHiddenApiExemptionsMethod.invoke(vmRuntime, new Object[]{new String[]{"L"}});



            Class<?> ActivityTaskManagerclass = Class.forName("android.app.ActivityTaskManager");
            // 在安卓13上 获取 IActivityTaskManagerSingletonField 这个字段, 想办法替换它
            Field IActivityTaskManagerSingletonField = ActivityTaskManagerclass.getDeclaredField("IActivityTaskManagerSingleton");
            IActivityTaskManagerSingletonField.setAccessible(true);
            //获取静态变量
            Object IActivityTaskManagerSingleton = IActivityTaskManagerSingletonField.get(null);

            // 10以上是用的IActivityTaskManagerSingleton， android.util.Singleton对象; 我们取出这个单例里面的字段
            Class<?> singleton = Class.forName("android.util.Singleton");
            Field mInstanceField = singleton.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);

            // ActivityTaskManager IActivityTaskManagerSingleton 获取系统的 IActivityManager对象
           // Object rawIActivityManager  = mInstanceField.get(IActivityTaskManagerSingleton);
            // 2. 通过 ServiceManager 获取 ACTIVITY_TASK_SERVICE 的 IBinder
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
            getServiceMethod.setAccessible(true);
            // "activity_task" 是 ACTIVITY_TASK_SERVICE 的服务名（Context.ACTIVITY_TASK_SERVICE 的值）
            IBinder activityTaskBinder = (IBinder) getServiceMethod.invoke(null, "activity_task");

            // 3. 将 IBinder 转换为 IActivityTaskManager 实例（通过 Stub.asInterface）
            Class<?> iTaskManagerStubClass = Class.forName("android.app.IActivityTaskManager$Stub");
            @SuppressLint("BlockedPrivateApi") Method asInterfaceMethod = iTaskManagerStubClass.getDeclaredMethod("asInterface", IBinder.class);
            asInterfaceMethod.setAccessible(true);
            Object rawIActivityTaskManager = asInterfaceMethod.invoke(null, activityTaskBinder);


            if (rawIActivityTaskManager == null) {
                Log.e("hhhh HookError", "原始 IActivityTaskManager 实例为 null！");
            }else {
                Log.e("hhhh HookError", "原始 IActivityTaskManager 实例为！" + rawIActivityTaskManager);
            }
            // 创建一个这个对象的代理对象, 然后替换这个字段, 让我们的代理对象帮忙干活
            Class<?> iActivityManagerInterface = Class.forName("android.app.IActivityTaskManager");
            Object proxy = Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class<?>[]{iActivityManagerInterface},
                    new HookHandler(rawIActivityTaskManager));
                    mInstanceField.set(IActivityTaskManagerSingleton,proxy);


        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
         
     }

    
    //这个获取实例方法在安卓10到13已经 用不了了
    public static void hookActivityManager_android4() {  
        try {
            Class<?> activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");

            // 获取 gDefault 这个字段, 想办法替换它
            Field gDefaultField = activityManagerNativeClass.getDeclaredField("gDefault");
            gDefaultField.setAccessible(true);

            Object gDefault = gDefaultField.get(null);

            // 4.x以上的gDefault是一个 android.util.Singleton对象; 我们取出这个单例里面的字段
            Class<?> singleton = Class.forName("android.util.Singleton");
            Field mInstanceField = singleton.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);

            // ActivityManagerNative 的gDefault对象里面原始的 IActivityManager对象
            Object rawIActivityManager = mInstanceField.get(gDefault);

            // 创建一个这个对象的代理对象, 然后替换这个字段, 让我们的代理对象帮忙干活
            Class<?> iActivityManagerInterface = Class.forName("android.app.IActivityManager");
            Object proxy = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                    new Class<?>[] { iActivityManagerInterface }, new HookHandler(rawIActivityManager));
            mInstanceField.set(gDefault, proxy);

        } catch (Exception e) {
            throw new RuntimeException("Hook Failed", e);
        }

    }


    public static void hookPackageManager(Context context) {
        try {
            // 获取全局的ActivityThread对象
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            Object currentActivityThread = currentActivityThreadMethod.invoke(null);

            // 获取ActivityThread里面原始的 sPackageManager
            Field sPackageManagerField = activityThreadClass.getDeclaredField("sPackageManager");
            sPackageManagerField.setAccessible(true);
            Object sPackageManager = sPackageManagerField.get(currentActivityThread);

            // 准备好代理对象, 用来替换原始的对象
            Class<?> iPackageManagerInterface = Class.forName("android.content.pm.IPackageManager");
            Object proxy = Proxy.newProxyInstance(iPackageManagerInterface.getClassLoader(),
                    new Class<?>[] { iPackageManagerInterface },
                    new HookHandler(sPackageManager));

            // 1. 替换掉ActivityThread里面的 sPackageManager 字段
            sPackageManagerField.set(currentActivityThread, proxy);

            // 2. 替换 ApplicationPackageManager里面的 mPm对象
            PackageManager pm = context.getPackageManager();
            Field mPmField = pm.getClass().getDeclaredField("mPM");
            mPmField.setAccessible(true);
            mPmField.set(pm, proxy);
        } catch (Exception e) {
            throw new RuntimeException("hook failed", e);
        }
    }
}
