/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.jersey;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.core.spi.component.ioc.IoCComponentProvider;
import com.sun.jersey.core.spi.component.ioc.IoCComponentProviderFactory;
import com.sun.jersey.core.spi.component.ioc.IoCManagedComponentProvider;
import jetbrains.buildServer.ExtensionHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 02.08.11 14:34
 */
public class ExtensionHolderProviderFactory implements IoCComponentProviderFactory {
  private static final Logger LOG = Logger.getInstance(ExtensionHolderProviderFactory.class.getName());

  private final ExtensionHolder myExtensionHolder;

  public ExtensionHolderProviderFactory(@NotNull ExtensionHolder extensionHolder) {
    myExtensionHolder = extensionHolder;
  }

  public IoCComponentProvider getComponentProvider(@NotNull final Class<?> c) {
    return getComponentProvider(null, c);
  }

  public IoCComponentProvider getComponentProvider(@Nullable final ComponentContext cc,
                                                   @NotNull final Class<?> c) {
    LOG.debug("Request for class: " + c);

    final Object o = myExtensionHolder.findSingletonService(c);
    if (o != null) {
      return new IoCManagedComponentProvider() {
        public Object getInstance() {
          return myExtensionHolder.findSingletonService(c);
        }

        public ComponentScope getScope() {
          return ComponentScope.Singleton;
        }

        public Object getInjectableInstance(Object o) {
          return o;
        }
      };
    }
    return null;
  }
}