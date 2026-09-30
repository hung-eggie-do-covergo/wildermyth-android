"""Minimal jnigen: turns Jamepad's inline `native ...; /* body */` methods into JNI C++."""
import re, sys

JT = {'boolean': 'jboolean', 'int': 'jint', 'long': 'jlong', 'void': 'void', 'String': 'jstring',
      'float': 'jfloat', 'double': 'jdouble', 'short': 'jshort', 'byte': 'jbyte'}
ARR = {'byte[]': ('jbyteArray', 'char*'), 'int[]': ('jintArray', 'int*'), 'float[]': ('jfloatArray', 'float*')}
SIG = re.compile(r'(?:public|private|protected)?\s*(static\s+)?native\s+([\w\[\]]+)\s+(\w+)\s*\(([^)]*)\)\s*;\s*/\*(.*?)\*/', re.S)
TOP = re.compile(r'/\*JNI(.*?)\*/', re.S)

def mangle(s): return s.replace('_', '_1')

def convert(path, cls):
    src = open(path).read()
    out = [m.group(1) for m in TOP.finditer(src)]
    for m in SIG.finditer(src):
        static, ret, name, params, body = m.groups()
        args, pre, post = [], [], []
        for p in [p.strip() for p in params.split(',') if p.strip()]:
            t, n = p.rsplit(None, 1)
            if t == 'String':
                args.append(f'jstring obj_{n}')
                pre.append(f'char* {n} = (char*)env->GetStringUTFChars(obj_{n}, 0);')
                post.append(f'env->ReleaseStringUTFChars(obj_{n}, {n});')
            elif t in ARR:
                jt, ct = ARR[t]
                args.append(f'{jt} obj_{n}')
                pre.append(f'{ct} {n} = ({ct})env->GetPrimitiveArrayCritical(obj_{n}, 0);')
                post.append(f'env->ReleasePrimitiveArrayCritical(obj_{n}, {n}, 0);')
            else:
                args.append(f'{JT[t]} {n}')
        jret = JT[ret]
        head = f'extern "C" JNIEXPORT {jret} JNICALL Java_com_studiohartman_jamepad_{cls}_{mangle(name)}' \
               f'(JNIEnv* env, {"jclass clazz" if static else "jobject object"}{"".join(", " + a for a in args)})'
        if post:
            # Run the body in a lambda so every return path still releases what was pinned.
            r = 'auto _r = ' if ret != 'void' else ''
            fn = f'{head} {{\n  {" ".join(pre)}\n  {r}[&]() -> {jret} {{{body}}}();\n  {" ".join(post)}\n  {"return _r;" if ret != "void" else ""}\n}}'
        else:
            fn = f'{head} {{{body}}}'
        out.append(fn)
    return '\n'.join(out)

# SDL is initialised by the app's Android side (SDL_Init from this thread crashes), so only init
# here when that has not happened.
PATCHES = {'if (SDL_Init(': 'SDL_SetMainReady();\n        if (SDL_WasInit(SDL_INIT_EVENTS | SDL_INIT_JOYSTICK | SDL_INIT_GAMECONTROLLER) != (SDL_INIT_EVENTS | SDL_INIT_JOYSTICK | SDL_INIT_GAMECONTROLLER) && SDL_Init('}

base = sys.argv[1]
print('#include <jni.h>\n#include <stdio.h>')
# The desktop JVM resolves JNI_OnLoad through dependencies too and would run SDL's Android one,
# which needs Android classes; SDL is already initialised by the app's Android side.
print('extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) { return JNI_VERSION_1_6; }')
for cls in ('ControllerManager', 'ControllerIndex'):
    code = convert(f'{base}/{cls}.java', cls)
    for k, v in PATCHES.items(): code = code.replace(k, v)
    print(code)
