package name.monwf.customiuizer.mods.utils;

import java.lang.reflect.Member;

import io.github.libxposed.api.XposedInterface;

/**
 * Adapts the module's callback style to the libxposed API 101 chain hooker.
 */
public class HookerClassHelper {
    public static class MethodHookParam {
        private enum Phase {
            BEFORE,
            AFTER
        }

        private final Member member;
        private final Object thisObject;
        private final Object[] args;
        private Object result;
        private Throwable throwable;
        private boolean returnEarly;
        private Phase phase;

        MethodHookParam(Member member, Object thisObject, Object[] args) {
            this.member = member;
            this.thisObject = thisObject;
            this.args = args;
            this.phase = Phase.BEFORE;
        }

        public Member getMember() {
            return member;
        }

        public Object getThisObject() {
            return thisObject;
        }

        public Object[] getArgs() {
            return args;
        }

        public int getArgsCount() {
            return args.length;
        }

        public Object getResult() {
            return result;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setResult(Object result) {
            this.result = result;
        }

        public void returnAndSkip(Object result) {
            if (phase != Phase.BEFORE) {
                throw new IllegalStateException("returnAndSkip can only be called from beforeHookedMethod");
            }
            this.result = result;
            this.returnEarly = true;
        }

        boolean isReturnEarly() {
            return returnEarly;
        }

        void enterAfterPhase(Object result, Throwable throwable) {
            this.result = result;
            this.throwable = throwable;
            this.phase = Phase.AFTER;
        }
    }

    public static class MethodHook {
        public int mPriority;

        public MethodHook() {
            this(XposedInterface.PRIORITY_DEFAULT);
        }

        public MethodHook(int priority) {
            mPriority = priority;
        }

        public final void beforeHook(MethodHookParam callback) throws Throwable {
            this.before(callback);
        }

        public final void afterHook(MethodHookParam callback) throws Throwable {
            this.after(callback);
        }

        protected void before(MethodHookParam callback) throws Throwable {
        }

        protected void after(MethodHookParam callback) throws Throwable {
        }
    }

    public static XposedInterface.Hooker newHooker(MethodHook hook) {
        return chain -> {
            Member member = chain.getExecutable();
            Object[] args = chain.getArgs().toArray(new Object[0]);

            MethodHookParam callback = new MethodHookParam(member, chain.getThisObject(), args);
            hook.beforeHook(callback);

            Object result = null;
            Throwable throwable = null;
            if (callback.isReturnEarly()) {
                result = callback.getResult();
            } else {
                try {
                    result = chain.proceed(callback.args);
                } catch (Throwable t) {
                    throwable = t;
                }
            }

            callback.enterAfterPhase(result, throwable);
            hook.afterHook(callback);

            if (throwable != null) {
                throw throwable;
            }
            return callback.getResult();
        };
    }

    /** Predefined callback that skips the method without replacements. */
    public static final MethodHook DO_NOTHING = new MethodHook(XposedInterface.PRIORITY_HIGHEST) {
        @Override
        protected void before(MethodHookParam param) {
            param.returnAndSkip(null);
        }
    };

    /** Creates a callback which always returns a specific value. */
    public static MethodHook returnConstant(final Object result) {
        return returnConstant(XposedInterface.PRIORITY_HIGHEST, result);
    }

    /** Creates a callback which always returns a specific value at a specified priority. */
    public static MethodHook returnConstant(int priority, final Object result) {
        return new MethodHook(priority) {
            @Override
            protected void before(MethodHookParam param) {
                param.returnAndSkip(result);
            }
        };
    }
}