/*
 * Copyright 2010-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.rt;

import jet.TypeInfo;
import jet.runtime.typeinfo.JetClass;
import jet.typeinfo.TypeInfoProjection;
import jet.typeinfo.TypeInfoVariance;
import org.jetbrains.jet.rt.signature.JetSignatureExceptionsAdapter;
import org.jetbrains.jet.rt.signature.JetSignatureReader;
import org.jetbrains.jet.rt.signature.JetSignatureVariance;
import org.jetbrains.jet.rt.signature.JetSignatureVisitor;

import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
* @author Stepan Koltsov
*/
class TypeInfoParser {
    static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    static final WeakHashMap<Class,Signature> map = new WeakHashMap<Class,Signature>();

    public static Signature parse(Class klass) {
        if(klass == null)
            return null;

//            lock.readLock().lock();
        Signature sig = map.get(klass);
//            lock.readLock().unlock();
        if (sig == null) {
//                lock.writeLock().lock();

            sig = map.get(klass);
            if (sig == null) {
                sig = internalParse(klass);
            }

//                lock.writeLock().unlock();
        }
        return sig;
    }

    private static Signature internalParse(Class klass) {
        if(klass.isArray() && !klass.getComponentType().isPrimitive()) {
            Signature signature = new ArraySignature(klass);
            map.put(klass, signature);
            return signature;
        }

        JetClass annotation = (JetClass) klass.getAnnotation(JetClass.class);
        if(annotation != null) {
            String value = annotation.signature();
            if(value != null) {
                Class enclosingClass = klass.getEnclosingClass();
                Signature signature = new Signature(klass);
                map.put(klass, signature);
                parse(value, signature);
                signature.afterParse();
                return signature;
            }
        }
        return getGenericSignature(klass);
    }

    private static void parse(String annotationValue, final Signature signature) {
        signature.variables = new ArrayList<TypeInfoProjection>();
        signature.varNames = new HashMap<String, Integer>();
        signature.superTypes = new ArrayList<TypeInfo>();
        new JetSignatureReader(annotationValue).accept(new JetSignatureExceptionsAdapter() {

            @Override
            public JetSignatureVisitor visitFormalTypeParameter(final String name, final TypeInfoVariance variance, boolean reified) {

                // TODO: nullability

                return new JetSignatureExceptionsAdapter() {
                    @Override
                    public JetSignatureVisitor visitClassBound() {
                        return new SignatureParserJetSignatureAdapter(signature.klazz.getClassLoader(), signature) {
                            @Override
                            protected void done(TypeInfo typeInfo) {
                                // TODO
                            }
                        };
                    }

                    @Override
                    public JetSignatureVisitor visitInterfaceBound() {
                        return new SignatureParserJetSignatureAdapter(signature.klazz.getClassLoader(), signature) {
                            @Override
                            protected void done(TypeInfo typeInfo) {
                                // TODO
                            }
                        };
                    }

                    @Override
                    public void visitFormalTypeParameterEnd() {
                        final TypeInfoVar typeInfoVar = new TypeInfoVar(signature, signature.variables.size());

                        signature.varNames.put(name, signature.variables.size());
                        TypeInfoProjection typeInfoProjection = new TypeInfoProjection() {
                            @Override
                            public TypeInfoVariance getVariance() {
                                return variance;
                            }

                            @Override
                            public TypeInfo getType() {
                                return typeInfoVar;
                            }

                            @Override
                            public String toString() {
                                return typeInfoVar.toString();
                            }
                        };

                        signature.variables.add(typeInfoProjection);
                    }
                };
            }

            @Override
            public JetSignatureVisitor visitSuperclass() {
                return new SignatureParserJetSignatureAdapter(signature.klazz.getClassLoader(), signature) {
                    @Override
                    protected void done(TypeInfo typeInfo) {
                        signature.superTypes.add(typeInfo);
                        signature.superSignatures.put(typeInfo.getJavaClass(), typeInfo);
                    }
                };
            }

            @Override
            public JetSignatureVisitor visitInterface() {
                return new SignatureParserJetSignatureAdapter(signature.klazz.getClassLoader(), signature) {
                    @Override
                    protected void done(TypeInfo typeInfo) {
                        signature.superTypes.add(typeInfo);
                        signature.superSignatures.put(typeInfo.getJavaClass(), typeInfo);
                    }
                };
            }
        });
    }


    private static abstract class SignatureParserJetSignatureAdapter extends JetSignatureExceptionsAdapter {
        private final ClassLoader classLoader;
        private final Signature signature;

        protected SignatureParserJetSignatureAdapter(ClassLoader classLoader, Signature signature) {
            this.classLoader = classLoader;
            this.signature = signature;
        }

        protected abstract void done(TypeInfo typeInfo);

        private Class<?> klass;
        private boolean nullable;
        private List<TypeInfoProjection> projections;

        @Override
        public void visitClassType(String name, boolean nullable, boolean forceReal) {
            try {
                // TODO: real flag
                klass = classLoader.loadClass(name.replace('/', '.'));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException();
            }
            this.nullable = nullable;
            this.projections = new ArrayList<TypeInfoProjection>();
        }

        private static TypeInfoVariance parseVariance(JetSignatureVariance variance) {
            switch (variance) {
                case INVARIANT: return TypeInfoVariance.INVARIANT;
                case OUT: return TypeInfoVariance.OUT;
                case IN: return TypeInfoVariance.IN;
                default: throw new IllegalStateException();
            }
        }

        @Override
        public JetSignatureVisitor visitTypeArgument(final JetSignatureVariance wildcard) {
            final TypeInfoVariance variance = parseVariance(wildcard);
            return new SignatureParserJetSignatureAdapter(classLoader, signature) {
                @Override
                protected void done(TypeInfo typeInfo) {
                    projections.add(new TypeInfoProjectionImpl(typeInfo) {
                        @Override
                        public TypeInfoVariance getVariance() {
                            return variance;
                        }
                    });
                }
            };
        }

        @Override
        public void visitTypeVariable(String name, boolean nullable) {
            Integer varIndex = signature.varNames.get(name);
            if (varIndex == null) {
                throw new IllegalStateException("unresolved type variable: " + name);
            }
            TypeInfoVar typeInfo = new TypeInfoVar(signature, nullable, varIndex);
            done(typeInfo);
        }

        @Override
        public void visitEnd() {
            done(new TypeInfoImpl(klass, nullable, projections.toArray(new TypeInfoProjection[0])));
        }
    }


    private static Signature getGenericSignature(Class klass) {
        // todo complete impl

        java.lang.reflect.Type genericSuperclass = klass.getGenericSuperclass();
        Signature signature = new Signature(klass);

        TypeVariable[] typeParameters = klass.getTypeParameters();
        if(typeParameters == null || typeParameters.length == 0) {
            signature.varNames = Collections.emptyMap();
            signature.variables = Collections.emptyList();
        }
        else {
            signature.varNames = new HashMap<String, Integer>();
            signature.variables = new LinkedList<TypeInfoProjection>();
        }

        if (typeParameters != null && typeParameters.length != 0) {
            for (int i = 0; i < typeParameters.length; i++) {
                TypeVariable typeParameter = typeParameters[i];
                signature.varNames.put(typeParameter.getName(), i);
                final TypeInfoVar typeInfoVar = new TypeInfoVar(signature, false, i);
                signature.variables.add(new TypeInfoProjection(){

                    @Override
                    public TypeInfoVariance getVariance() {
                        return TypeInfoVariance.INVARIANT;
                    }

                    @Override
                    public TypeInfo getType() {
                        return typeInfoVar;
                    }

                    @Override
                    public String toString() {
                        return typeInfoVar.toString();
                    }
                });
            }
        }

        return signature;
    }

    private static class ArraySignature extends Signature {
        public ArraySignature(Class klass) {
            super(klass);
            variables  = new LinkedList<TypeInfoProjection>();
            varNames = new HashMap<String, Integer>();
            varNames.put("T", 0);
            final TypeInfoVar typeInfoVar = new TypeInfoVar(this, 0);
            variables.add(new TypeInfoProjection() {
                @Override
                public TypeInfoVariance getVariance() {
                    return TypeInfoVariance.INVARIANT;
                }

                @Override
                public TypeInfo getType() {
                    return typeInfoVar;
                }
            });
        }
    }
}
