import React from 'react';
import { Menu, Transition } from '@headlessui/react';
import { UserCircleIcon, ArrowRightOnRectangleIcon, ChevronDownIcon } from '@heroicons/react/24/solid';
import { useAuth } from '../contexts/AuthContext';

export const UserProfile: React.FC = () => {
  const { user, logout } = useAuth();

  return (
    <Menu as="div" className="relative">
      {/* Menu Button - Avatar with email */}
      <Menu.Button className="flex items-center space-x-2 p-1 rounded-lg hover:bg-gray-100 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500">
        {user?.pictureUrl ? (
          <img
            src={user.pictureUrl}
            alt={user.name || 'User'}
            className="w-8 h-8 rounded-full object-cover"
          />
        ) : (
          <UserCircleIcon className="w-8 h-8 text-gray-600" />
        )}
        <div className="hidden md:flex flex-col items-start">
          <span className="text-sm text-neutral-700">{user?.email}</span>
        </div>
        <ChevronDownIcon className="w-4 h-4 text-gray-500" />
      </Menu.Button>

      {/* Dropdown Menu */}
      <Transition
        enter="transition ease-out duration-100"
        enterFrom="transform opacity-0 scale-95"
        enterTo="transform opacity-100 scale-100"
        leave="transition ease-in duration-75"
        leaveFrom="transform opacity-100 scale-100"
        leaveTo="transform opacity-0 scale-0 scale-95"
      >
        <Menu.Items className="absolute right-0 mt-2 w-48 origin-top-right bg-white rounded-lg shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none z-50">
          {/* Logout Menu Item */}
          <Menu.Item>
            {({ active }) => (
              <button
                onClick={logout}
                className={`w-full flex items-center px-4 py-2 text-sm ${
                  active ? 'bg-gray-100' : ''
                }`}
              >
                <ArrowRightOnRectangleIcon className="w-4 h-4 mr-3 text-gray-500" />
                Sign out
              </button>
            )}
          </Menu.Item>
        </Menu.Items>
      </Transition>
    </Menu>
  );
};
