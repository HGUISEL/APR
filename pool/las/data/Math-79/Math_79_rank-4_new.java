/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.geronimo.blueprint.container;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.geronimo.blueprint.BeanProcessor;
import org.apache.geronimo.blueprint.ExtendedBlueprintContainer;
import org.apache.geronimo.blueprint.di.AbstractRecipe;
import org.apache.geronimo.blueprint.di.Recipe;
import org.apache.geronimo.blueprint.utils.ReflectionUtils;
import static org.apache.geronimo.blueprint.utils.ReflectionUtils.getRealCause;
import org.osgi.service.blueprint.container.ReifiedType;
import org.osgi.service.blueprint.container.ComponentDefinitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <code>Recipe</code> to create POJOs.
 *
 * @author <a href="mailto:dev@geronimo.apache.org">Apache Geronimo Project</a>
 * @version $Rev$, $Date$
 */
public class BeanRecipe extends AbstractRecipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(BeanRecipe.class);

    private final ExtendedBlueprintContainer blueprintContainer;
    private final LinkedHashMap<String,Object> properties = new LinkedHashMap<String,Object>();
    private final Object type;

    private String initMethod;
    private String destroyMethod;
    private List<Recipe> explicitDependencies;
    
    private Recipe factory;
    private String factoryMethod;
    private List<Object> arguments;
    private List<String> argTypes;
    private boolean reorderArguments;


    public BeanRecipe(String name, ExtendedBlueprintContainer blueprintContainer, Object type) {
        super(name);
        this.blueprintContainer = blueprintContainer;
        this.type = type;
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    public Map<String, Object> getProperties() {
        return new LinkedHashMap<String, Object>(properties);
    }

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    public void setFactoryMethod(String method) {
        this.factoryMethod = method;
    }
    
    public void setFactoryComponent(Recipe factory) {
        this.factory = factory;
    }
    
    public void setArgTypes(List<String> argTypes) {
        this.argTypes = argTypes;
    }
    
    public void setArguments(List<Object> arguments) {
        this.arguments = arguments;
    }
    
    public void setReorderArguments(boolean reorder) {
        this.reorderArguments = reorder;
    }
    
    public void setInitMethod(String initMethod) {
        this.initMethod = initMethod;
    }
    
    public String getInitMethod() {
        return initMethod;
    }
    
    public void setDestroyMethod(String destroyMethod) {
        this.destroyMethod = destroyMethod;
    }
    
    public String getDestroyMethod() {
        return destroyMethod;
    }

    public List<Recipe> getExplicitDependencies() {
        return explicitDependencies;
    }

    public void setExplicitDependencies(List<Recipe> explicitDependencies) {
        this.explicitDependencies = explicitDependencies;
    }

    @Override
    public List<Recipe> getConstructorDependencies() {
        List<Recipe> recipes = new ArrayList<Recipe>();
        if (explicitDependencies != null) {
            recipes.addAll(explicitDependencies);
        }
        if (arguments != null) {
            for (Object argument : arguments) {
                if (argument instanceof Recipe) {
                    recipes.add((Recipe)argument);
                }
            }
        }
        return recipes;
    }
    
    public List<Recipe> getDependencies() {
        List<Recipe> recipes = new ArrayList<Recipe>();
        for (Object o : properties.values()) {
            if (o instanceof Recipe) {
                Recipe recipe = (Recipe) o;
                recipes.add(recipe);
            }
        }
        recipes.addAll(getConstructorDependencies());
        return recipes; 
    }

    private void instantiateExplicitDependencies() {
        if (explicitDependencies != null) {
            for (Recipe recipe : explicitDependencies) {
                recipe.create();
            }
        }
    }

    @Override
    protected Class loadClass(String className) {
        ClassLoader loader = type instanceof Class ? ((Class) type).getClassLoader() : null;
        ReifiedType t = loadType(className, loader);
        return t != null ? t.getRawClass() : null;
    }

    @Override
    protected ReifiedType loadType(String className) {
        return loadType(className, type instanceof Class ? ((Class) type).getClassLoader() : null);
    }

    private Object getInstance() throws ComponentDefinitionException {
        Object instance;
        
        // Instanciate arguments
        List<Object> args = new ArrayList<Object>();
        List<ReifiedType> argTypes = new ArrayList<ReifiedType>();
        if (arguments != null) {
            for (int i = 0; i < arguments.size(); i++) {
                Object arg = arguments.get(i);
                if (arg instanceof Recipe) {
                    args.add(((Recipe) arg).create());
                } else {
                    args.add(arg);
                }
                if (this.argTypes != null) {
                    argTypes.add(this.argTypes.get(i) != null ? loadType(this.argTypes.get(i)) : null);
                }
            }
        }

        if (factory != null) {
            // look for instance method on factory object
            Object factoryObj = factory.create();
            // Map of matching methods
            Map<Method, List<Object>> matches = findMatchingMethods(factoryObj.getClass(), factoryMethod, true, args, argTypes);
            if (matches.size() == 1) {
                try {
                    Map.Entry<Method, List<Object>> match = matches.entrySet().iterator().next();
                    instance = invoke(match.getKey(), factoryObj, match.getValue().toArray());
                } catch (Throwable e) {
                    throw new ComponentDefinitionException("Error when instanciating bean " + getName() + " of class " + getType(), getRealCause(e));
                }
            } else if (matches.size() == 0) {
                throw new ComponentDefinitionException("Unable to find a matching factory method " + factoryMethod + " on class " + factoryObj.getClass().getName() + " for arguments " + args + " when instanciating bean " + getName());
            } else {
                throw new ComponentDefinitionException("Multiple matching factory methods " + factoryMethod + " found on class " + factoryObj.getClass().getName() + " for arguments " + args + " when instanciating bean " + getName() + ": " + matches.keySet());
            }
        } else if (factoryMethod != null) {
            // Map of matching methods
            Map<Method, List<Object>> matches = findMatchingMethods(getType(), factoryMethod, false, args, argTypes);
            if (matches.size() == 1) {
                try {
                    Map.Entry<Method, List<Object>> match = matches.entrySet().iterator().next();
                    instance = invoke(match.getKey(), null, match.getValue().toArray());
                } catch (Throwable e) {
                    throw new ComponentDefinitionException("Error when instanciating bean " + getName() + " of class " + getType(), getRealCause(e));
                }
            } else if (matches.size() == 0) {
                throw new ComponentDefinitionException("Unable to find a matching factory method " + factoryMethod + " on class " + getType().getName() + " for arguments " + args + " when instanciating bean " + getName());
            } else {
                throw new ComponentDefinitionException("Multiple matching factory methods " + factoryMethod + " found on class " + getType().getName() + " for arguments " + args + " when instanciating bean " + getName() + ": " + matches.keySet());
            }
        } else {
            if (getType() == null) {
                throw new ComponentDefinitionException("No factoryMethod nor class is defined for this bean");
            }
            // Map of matching constructors
            Map<Constructor, List<Object>> matches = findMatchingConstructors(getType(), args, argTypes);
            if (matches.size() == 1) {
                try {
                    Map.Entry<Constructor, List<Object>> match = matches.entrySet().iterator().next();
                    instance = newInstance(match.getKey(), match.getValue().toArray());
                } catch (Throwable e) {
                    throw new ComponentDefinitionException("Error when instanciating bean " + getName() + " of class " + getType(), getRealCause(e));
                }
            } else if (matches.size() == 0) {
                throw new ComponentDefinitionException("Unable to find a matching constructor on class " + getType().getName() + " for arguments " + args + " when instanciating bean " + getName());
            } else {
                throw new ComponentDefinitionException("Multiple matching constructors found on class " + getType().getName() + " for arguments " + args + " when instanciating bean " + getName() + ": " + matches.keySet());
            }
        }
        
        return instance;
    }

    private Map<Method, List<Object>> findMatchingMethods(Class type, String name, boolean instance, List<Object> args, List<ReifiedType> types) {
        Map<Method, List<Object>> matches = new HashMap<Method, List<Object>>();
        // Get constructors
        List<Method> methods = new ArrayList<Method>(Arrays.asList(type.getMethods()));
        // Discard any signature with wrong cardinality
        for (Iterator<Method> it = methods.iterator(); it.hasNext();) {
            Method mth = it.next();
            if (!mth.getName().equals(name)) {
                it.remove();
            } else if (mth.getParameterTypes().length != args.size()) {
                it.remove();
            } else if (instance ^ !Modifier.isStatic(mth.getModifiers())) {
                it.remove();
            }
        }
        // Find a direct match with assignment
        if (matches.size() != 1) {
            Map<Method, List<Object>> nmatches = new HashMap<Method, List<Object>>();
            for (Method mth : methods) {
                boolean found = true;
                List<Object> match = new ArrayList<Object>();
                for (int i = 0; i < args.size(); i++) {
                    ReifiedType argType = new GenericType(mth.getGenericParameterTypes()[i]);
                    if (types.get(i) != null && !argType.getRawClass().equals(types.get(i).getRawClass())) {
                        found = false;
                        break;
                    }
                    if (!AggregateConverter.isAssignable(args.get(i), argType)) {
                        found = false;
                        break;
                    }
                    try {
                        match.add(convert(args.get(i), mth.getGenericParameterTypes()[i]));
                    } catch (Throwable t) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    nmatches.put(mth, match);
                }
            }
            if (nmatches.size() > 0) {
                matches = nmatches;
            }
        }
        // Find a direct match with conversion
        if (matches.size() != 1) {
            Map<Method, List<Object>> nmatches = new HashMap<Method, List<Object>>();
            for (Method mth : methods) {
                boolean found = true;
                List<Object> match = new ArrayList<Object>();
                for (int i = 0; i < args.size(); i++) {
                    ReifiedType argType = new GenericType(mth.getGenericParameterTypes()[i]);
                    if (types.get(i) != null && !argType.getRawClass().equals(types.get(i).getRawClass())) {
                        found = false;
                        break;
                    }
                    try {
                        Object val = convert(args.get(i), argType);
                        match.add(val);
                    } catch (Throwable t) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    nmatches.put(mth, match);
                }
            }
            if (nmatches.size() > 0) {
                matches = nmatches;
            }
        }
        // Start reordering with assignment
        if (matches.size() != 1 && reorderArguments && args.size() > 1) {
            Map<Method, List<Object>> nmatches = new HashMap<Method, List<Object>>();
            for (Method mth : methods) {
                ArgumentMatcher matcher = new ArgumentMatcher(mth.getGenericParameterTypes(), false);
                List<Object> match = matcher.match(args, types);
                if (match != null) {
                    nmatches.put(mth, match);
                }
            }
            if (nmatches.size() > 0) {
                matches = nmatches;
            }
        }
        // Start reordering with conversion
        if (matches.size() != 1 && reorderArguments && args.size() > 1) {
            Map<Method, List<Object>> nmatches = new HashMap<Method, List<Object>>();
            for (Method mth : methods) {
                ArgumentMatcher matcher = new ArgumentMatcher(mth.getGenericParameterTypes(), true);
                List<Object> match = matcher.match(args, types);
                if (match != null) {
                    nmatches.put(mth, match);
                }
            }
            if (nmatches.size() > 0) {
                matches = nmatches;
            }
        }
        return matches;
    }

    private Map<Constructor, List<Object>> findMatchingConstructors(Class type, List<Object> args, List<ReifiedType> types) {
        Map<Constructor, List<Object>> matches = new HashMap<Constructor, List<Object>>();
        // Get constructors
        List<Constructor> constructors = new ArrayList<Constructor>(Arrays.asList(type.getConstructors()));
        // Discard any signature with wrong cardinality
        for (Iterator<Constructor> it = constructors.iterator(); it.hasNext();) {
            if (it.next().getParameterTypes().length != args.size()) {
                it.remove();
            }
        }
        // Find a direct match with assignment
        if (matches.size() != 1) {
            Map<Constructor, List<Object>> nmatches = new HashMap<Constructor, List<Object>>();
            for (Constructor cns : constructors) {
                boolean found = true;
                List<Object> match = new ArrayList<Object>();
                for (int i = 0; i < args.size(); i++) {
                    ReifiedType argType = new GenericType(cns.getGenericParameterTypes()[i]);
                    if (types.get(i) != null && !argType.getRawClass().equals(types.get(i).getRawClass())) {
                        found = false;
                        break;
                    }
                    if (!AggregateConverter.isAssignable(args.get(i), argType)) {
                        found = false;
                        break;
                    }
                    try {
                        match.add(convert(args.get(i), cns.getGenericParameterTypes()[i]));
                    } catch (Throwable t) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    nmatches.put(cns, match);
                }
            }
            if (nmatches.size() > 0) {
                matches = nmatches;
            }
        }
        // Find a direct match with conversion
        if (matches.size() != 1) {
            Map<Constructor, List<Object>> nmatches = new HashMap<Constructor, List<Object>>();
            for (Constructor cns : constructors) {
                boolean found = true;
                List<Object> match = new ArrayList<Object>();
                for (int i = 0; i < args.size(); i++) {
                    ReifiedType argType = new GenericType(cns.getGenericParameterTypes()[i]);
                    if (types.get(i) != null && !argType.getRawClass().equals(types.get(i).getRawClass())) {
                        found = false;
                        break;
                    }
                    try {
                        Object val = convert(args.get(i), argType);
                        match.add(val);
                    } catch (Throwable t) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    nmatches.put(cns, match);
                }
            }
            if (nmatches.size() > 0) {
                matches = nmatches;
            }
        }
        // Start reordering with assignment
        if (matches.size() != 1 && reorderArguments && arguments.size() > 1) {
            Map<Constructor, List<Object>> nmatches = new HashMap<Constructor, List<Object>>();
            for (Constructor cns : constructors) {
                ArgumentMatcher matcher = new ArgumentMatcher(cns.getGenericParameterTypes(), false);
                List<Object> match = matcher.match(args, types);
                if (match != null) {
                    nmatches.put(cns, match);
                }
            }
            if (nmatches.size() > 0) {
                matches = nmatches;
            }
        }
        // Start reordering with conversion
        if (matches.size() != 1 && reorderArguments && arguments.size() > 1) {
            Map<Constructor, List<Object>> nmatches = new HashMap<Constructor, List<Object>>();
            for (Constructor cns : constructors) {
                ArgumentMatcher matcher = new ArgumentMatcher(cns.getGenericParameterTypes(), true);
                List<Object> match = matcher.match(args, types);
                if (match != null) {
                    nmatches.put(cns, match);
                }
            }
            if (nmatches.size() > 0) {
                matches = nmatches;
            }
        }
        return matches;
    }

    /**
     * Returns init method (if any). Throws exception if the init-method was set explicitly on the bean
     * and the method is not found on the instance.
     */
    protected Method getInitMethod(Object instance) throws ComponentDefinitionException {
        Method method = null;        
        if (initMethod != null && initMethod.length() > 0) {
            method = ReflectionUtils.getLifecycleMethod(instance.getClass(), initMethod);
            if (method == null) {
                throw new ComponentDefinitionException("Component '" + getName() + "' does not have init-method: " + initMethod);
            }
        }
        return method;
    }

    /**
     * Returns destroy method (if any). Throws exception if the destroy-method was set explicitly on the bean
     * and the method is not found on the instance.
     */
    public Method getDestroyMethod(Object instance) throws ComponentDefinitionException {
        Method method = null;        
        if (destroyMethod != null && destroyMethod.length() > 0) {
            method = ReflectionUtils.getLifecycleMethod(instance.getClass(), destroyMethod);
            if (method == null) {
                throw new ComponentDefinitionException("Component '" + getName() + "' does not have destroy-method: " + destroyMethod);
            }
        }
        return method;
    }
    
    @Override
    protected Object internalCreate() throws ComponentDefinitionException {
        
        instantiateExplicitDependencies();

        Object obj = getInstance();
                
        // check for init lifecycle method (if any)
        Method initMethod = getInitMethod(obj);
        
        // check for destroy lifecycle method (if any)
        getDestroyMethod(obj);
        
        // Add partially created object to the container
//        if (initMethod == null) {
            addPartialObject(obj);
//        }

        // inject properties
        setProperties(obj);

        for (BeanProcessor processor : blueprintContainer.getProcessors(BeanProcessor.class)) {
            obj = processor.beforeInit(obj, getName());
        }
        
        // call init method
        if (initMethod != null) {
            try {
                invoke(initMethod, obj, null);
            } catch (Throwable t) {
                throw new ComponentDefinitionException("Unable to intialize bean " + getName(), getRealCause(t));
            }
        }
        
        return obj;
    }
    
    public void destroyInstance(Object obj) {
        for (BeanProcessor processor : blueprintContainer.getProcessors(BeanProcessor.class)) {
            processor.beforeDestroy(obj, getName());
        }
        try {
            Method method = getDestroyMethod(obj);
            if (method != null) {
                invoke(method, obj, null);
            }
        } catch (Exception e) {
            LOGGER.info("Error invoking destroy method", getRealCause(e));
        }
        for (BeanProcessor processor : blueprintContainer.getProcessors(BeanProcessor.class)) {
            processor.afterDestroy(obj, getName());
        }
    }

    @Override
    public void destroy(Object instance) {
        Method method = getDestroyMethod(instance);
        if (method != null) {
            try {
                invoke(method, instance, null);
            } catch (Throwable e) {
                LOGGER.info("Error destroying bean " + getName(), getRealCause(e));
            }
        }
    }

    public void setProperties(Object instance) throws ComponentDefinitionException {
        // clone the properties so they can be used again
        Map<String,Object> propertyValues = new LinkedHashMap<String,Object>(properties);
        setProperties(propertyValues, instance, instance.getClass());
    }

    public Class getType() {
        if (type instanceof Class) {
            return (Class) type;
        } else if (type instanceof String) {
            return loadClass((String) type);
        } else {
            return null;
        }
    }

    private void setProperties(Map<String, Object> propertyValues, Object instance, Class clazz) {
        // set remaining properties
        for (Map.Entry<String, Object> entry : propertyValues.entrySet()) {
            String propertyName = entry.getKey();
            Object propertyValue = entry.getValue();

            setProperty(instance, clazz, propertyName, propertyValue);
        }

    }

    private void setProperty(Object instance, Class clazz, String propertyName, Object propertyValue) {
        String[] names = propertyName.split("\\.");
        for (int i = 0; i < names.length - 1; i++) {
            Method getter = getPropertyDescriptor(clazz, names[i]).getGetter();
            if (getter != null) {
                try {
                    instance = invoke(getter, instance, null);
                } catch (Exception e) {
                    throw new ComponentDefinitionException("Error getting property: " + names[i] + " on bean " + getName() + " when setting property " + propertyName + " on class " + clazz.getName(), getRealCause(e));
                }
                if (instance == null) {
                    throw new ComponentDefinitionException("Error setting compound property " + propertyName + " on bean " + getName() + ". Property " + names[i] + " is null");
                }
                clazz = instance.getClass();
            } else {
                throw new ComponentDefinitionException("No getter for " + names[i] + " property on bean " + getName() + " when setting property " + propertyName + " on class " + clazz.getName());
            }
        }
        Method setter = getPropertyDescriptor(clazz, names[names.length - 1]).getSetter();
        if (setter != null) {
            // convert the value to type of setter/field
            Type type = setter.getGenericParameterTypes()[0];
            // Instanciate value
            if (propertyValue instanceof Recipe) {
                propertyValue = ((Recipe) propertyValue).create();
            }
            try {
                propertyValue = convert(propertyValue, type);
            } catch (Exception e) {
                    String valueType = propertyValue == null ? "null" : propertyValue.getClass().getName();
                String memberType = type instanceof Class ? ((Class) type).getName() : type.toString();
                throw new ComponentDefinitionException("Unable to convert property value" +
                        " from " + valueType +
                        " to " + memberType +
                        " for injection " + setter, e);
            }
            try {
                // set value
                invoke(setter, instance, propertyValue);
            } catch (Exception e) {
                throw new ComponentDefinitionException("Error setting property: " + setter, getRealCause(e));
            }
        } else {
            throw new ComponentDefinitionException("No setter for " + names[names.length - 1] + " property");
        }
    }

    private ReflectionUtils.PropertyDescriptor getPropertyDescriptor(Class clazz, String name) {
        for (ReflectionUtils.PropertyDescriptor pd : ReflectionUtils.getPropertyDescriptors(clazz)) {
            if (pd.getName().equals(name)) {
                return pd;
            }
        }
        throw new ComponentDefinitionException("Unable to find property descriptor " + name + " on class " + clazz.getName());
    }
        
    private Object invoke(Method method, Object instance, Object... args) throws Exception {
        return ReflectionUtils.invoke(blueprintContainer.getAccessControlContext(), method, instance, args);        
    }
    
    private Object newInstance(Constructor constructor, Object... args) throws Exception {
        return ReflectionUtils.newInstance(blueprintContainer.getAccessControlContext(), constructor, args);         
    }
    
    private static Object UNMATCHED = new Object();

    private class ArgumentMatcher {

        private List<TypeEntry> entries;
        private boolean convert;

        public ArgumentMatcher(Type[] types, boolean convert) {
            entries = new ArrayList<TypeEntry>();
            for (Type type : types) {
                entries.add(new TypeEntry(new GenericType(type)));
            }
            this.convert = convert;
        }

        public List<Object> match(List<Object> arguments, List<ReifiedType> forcedTypes) {
            if (find(arguments, forcedTypes)) {
                return getArguments();
            }
            return null;
        }

        private List<Object> getArguments() {
            List<Object> list = new ArrayList<Object>();
            for (TypeEntry entry : entries) {
                if (entry.argument == UNMATCHED) {
                    throw new RuntimeException("There are unmatched types");
                } else {
                    list.add(entry.argument);
                }
            }
            return list;
        }

        private boolean find(List<Object> arguments, List<ReifiedType> forcedTypes) {
            if (entries.size() == arguments.size()) {
                boolean matched = true;
                for (int i = 0; i < arguments.size() && matched; i++) {
                    matched = find(arguments.get(i), forcedTypes.get(i));
                }
                return matched;
            }
            return false;
        }

        private boolean find(Object arg, ReifiedType forcedType) {
            for (TypeEntry entry : entries) {
                Object val = arg;
                if (entry.argument != UNMATCHED) {
                    continue;
                }
                if (forcedType != null) {
                    if (!forcedType.equals(entry.type)) {
                        continue;
                    }
                } else if (arg != null) {
                    if (convert) {
                        try {
                            // TODO: call canConvert instead of convert()
                            val = convert(arg, entry.type);
                        } catch (Throwable t) {
                            continue;
                        }
                    } else {
                        if (!AggregateConverter.isAssignable(arg, entry.type)) {
                            continue;
                        }
                    }
                }
                entry.argument = val;
                return true;
            }
            return false;
        }

    }

    private static class TypeEntry {

        private final ReifiedType type;
        private Object argument;

        public TypeEntry(ReifiedType type) {
            this.type = type;
            this.argument = UNMATCHED;
        }

    }

}
