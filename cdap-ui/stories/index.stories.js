import React from 'react';

import { storiesOf } from '@storybook/react';
import { action } from '@storybook/addon-actions';
import { linkTo } from '@storybook/addon-links';

import { Welcome } from '@storybook/react/demo';
import BtnWithLoading from '../app/cdap/components/BtnWithLoading';

storiesOf('Welcome', module).add('to Storybook', () => <Welcome showApp={linkTo('Button')} />);

storiesOf('Button', module)
  .add('with text', () => (
    <BtnWithLoading
      onClick={action('clicked')}
      label="Hello Button"
      loading={false}
      disabled={false}
    />
  ))
  .add('with some emoji', () => (
    <BtnWithLoading
      onClick={action('clicked')}
      label="😀 😎 👍 💯"
      loading={true}
      disabled={true}
    />
  ));
